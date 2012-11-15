package org.jetbrains.android.refactoring;

import com.android.resources.ResourceType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.dom.converters.AndroidResourceReferenceBase;
import org.jetbrains.android.dom.layout.LayoutDomFileDescription;
import org.jetbrains.android.dom.layout.LayoutViewElement;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.ValueResourceInfoImpl;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidFindStyleApplicationsProcessor extends BaseRefactoringProcessor {
  private final Module myModule;
  private final Map<AndroidAttributeInfo, String> myAttrMap;
  private final String myStyleName;
  private final XmlTag myStyleTag;
  private final XmlAttributeValue myStyleNameAttrValue;
  private final PsiElement myParentStyleNameAttrValue;
  private final PsiFile myContext;
  private boolean mySearchOnlyInCurrentModule;
  private VirtualFile myFileToScan;

  protected AndroidFindStyleApplicationsProcessor(@NotNull Module module,
                                                  @NotNull Map<AndroidAttributeInfo, String> attrMap,
                                                  @NotNull String styleName,
                                                  @NotNull XmlTag styleTag,
                                                  @NotNull XmlAttributeValue styleNameAttrValue,
                                                  @Nullable PsiElement parentStyleNameAttrValue,
                                                  @Nullable PsiFile context) {
    super(module.getProject());
    myModule = module;
    myAttrMap = attrMap;
    myStyleName = styleName;
    myStyleTag = styleTag;
    myParentStyleNameAttrValue = parentStyleNameAttrValue;
    myStyleNameAttrValue = styleNameAttrValue;
    myContext = context;
  }

  @NotNull
  @Override
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new UsageViewDescriptorAdapter() {
      @NotNull
      @Override
      public PsiElement[] getElements() {
        return new PsiElement[]{myStyleTag};
      }

      @Override
      public String getProcessedElementsHeader() {
        return "Style to use";
      }

      @Override
      public String getCodeReferencesText(int usagesCount, int filesCount) {
        return "Tags the reference to the style will be added to " +
               UsageViewBundle.getOccurencesString(usagesCount, filesCount);
      }
    };
  }

  @NotNull
  @Override
  protected UsageInfo[] findUsages() {
    final List<UsageInfo> usages = findAllStyleApplications();
    return usages.toArray(new UsageInfo[usages.size()]);
  }

  @Override
  protected boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
    super.preprocessUsages(refUsages);

    if (refUsages.get().length == 0) {
      Messages.showInfoMessage(myProject, "IDEA has not found any possible applications of style '" + myStyleName + "'",
                               AndroidBundle.message("android.find.style.applications.title"));
      return false;
    }
    return true;
  }

  @Override
  protected void performRefactoring(UsageInfo[] usages) {
    final Set<Pair<String, String>> attrsInStyle = new HashSet<Pair<String, String>>();

    for (AndroidAttributeInfo info : myAttrMap.keySet()) {
      attrsInStyle.add(Pair.create(info.getNamespace(), info.getName()));
    }

    for (UsageInfo usage : usages) {
      final PsiElement element = usage.getElement();
      final DomElement domElement = element instanceof XmlTag
                                    ? DomManager.getDomManager(myProject).getDomElement((XmlTag)element)
                                    : null;
      if (domElement instanceof LayoutViewElement) {
        final List<XmlAttribute> attributesToDelete = new ArrayList<XmlAttribute>();

        for (XmlAttribute attribute : ((XmlTag)element).getAttributes()) {
          if (attrsInStyle.contains(Pair.create(attribute.getNamespace(),
                                                attribute.getLocalName()))) {
            attributesToDelete.add(attribute);
          }
        }

        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            for (XmlAttribute attribute : attributesToDelete) {
              attribute.delete();
            }
            ((LayoutViewElement)domElement).getStyle().setStringValue("@style/" + myStyleName);
          }
        });
      }
    }
    final PsiFile file = myStyleTag.getContainingFile();

    if (file != null) {
      UndoUtil.markPsiFileForUndo(file);
    }

    if (myContext != null) {
      UndoUtil.markPsiFileForUndo(myContext);
    }
  }

  @Override
  protected String getCommandName() {
    return "Use Style '" + myStyleName + "' Where Possible";
  }

  @NotNull
  static List<Module> getAllModulesToScan(@NotNull Module module) {
    final List<Module> result = new ArrayList<Module>();

    for (Module m : ModuleManager.getInstance(module.getProject()).getModules()) {
      if (m.equals(module) || ModuleRootManager.getInstance(m).isDependsOn(module)) {
        result.add(module);
      }
    }
    return result;
  }

  @NotNull
  private List<UsageInfo> findAllStyleApplications() {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    final Project project = myModule.getProject();
    final List<VirtualFile> resDirs = new ArrayList<VirtualFile>();

    if (mySearchOnlyInCurrentModule) {
      collectResDir(myModule, myStyleNameAttrValue, myStyleName, resDirs);
    }
    else {
      for (Module m : getAllModulesToScan(myModule)) {
        collectResDir(m, myStyleNameAttrValue, myStyleName, resDirs);
      }
    }
    final List<VirtualFile> subdirs = AndroidResourceUtil.getResourceSubdirs(
      ResourceType.LAYOUT.getName(), resDirs.toArray(new VirtualFile[resDirs.size()]));

    List<VirtualFile> filesToProcess = new ArrayList<VirtualFile>();

    for (VirtualFile subdir : subdirs) {
      for (VirtualFile child : subdir.getChildren()) {
        if (child.getFileType() == XmlFileType.INSTANCE &&
            (myFileToScan == null || myFileToScan.equals(child))) {
          filesToProcess.add(child);
        }
      }
    }

    if (filesToProcess.size() == 0) {
      return Collections.emptyList();
    }
    final Set<PsiFile> psiFilesToProcess = new HashSet<PsiFile>();

    for (VirtualFile file : filesToProcess) {
      final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);

      if (psiFile != null) {
        psiFilesToProcess.add(psiFile);
      }
    }
    final CacheManager cacheManager = CacheManager.SERVICE.getInstance(project);
    final GlobalSearchScope projectScope = GlobalSearchScope.projectScope(project);

    for (Map.Entry<AndroidAttributeInfo, String> entry : myAttrMap.entrySet()) {
      filterFilesToScan(cacheManager, entry.getKey().getName(), psiFilesToProcess, projectScope);
      filterFilesToScan(cacheManager, entry.getValue(), psiFilesToProcess, projectScope);
    }

    if (psiFilesToProcess.size() == 0) {
      return Collections.emptyList();
    }
    final int n = psiFilesToProcess.size();
    int i = 0;

    if (indicator != null) {
      indicator.setText("Searching for style applications");
    }
    final List<UsageInfo> usages = new ArrayList<UsageInfo>();

    for (PsiFile psiFile : psiFilesToProcess) {
      ProgressManager.checkCanceled();

      final VirtualFile vFile = psiFile.getVirtualFile();
      if (vFile == null) {
        continue;
      }

      if (indicator != null) {
        indicator.setFraction((double)i / n);
        indicator.setText2(ProjectUtil.calcRelativeToProjectPath(vFile, project));
      }
      findAllStyleApplications(project, vFile, myAttrMap, myParentStyleNameAttrValue, usages);
    }
    return usages;
  }

  private static void collectResDir(Module module, XmlAttributeValue styleNameAttrValue, String styleName, List<VirtualFile> resDirs) {
    final AndroidFacet f = AndroidFacet.getInstance(module);
    final VirtualFile resDir = f != null
                               ? f.getLocalResourceManager().getResourceDir()
                               : null;
    if (resDir != null) {
      final List<ValueResourceInfoImpl> resolvedStyles = f.getLocalResourceManager().findValueResourceInfos(
        ResourceType.STYLE.getName(), styleName, true, false);

      if (resolvedStyles.size() == 1) {
        final XmlAttributeValue resolvedStyleNameElement = resolvedStyles.get(0).computeXmlElement();

        if (resolvedStyleNameElement != null &&
            resolvedStyleNameElement.equals(styleNameAttrValue)) {
          resDirs.add(resDir);
        }
      }
    }
  }

  private static void filterFilesToScan(CacheManager cacheManager,
                                        String s,
                                        Set<PsiFile> result,
                                        GlobalSearchScope scope) {
    for (String word : StringUtil.getWordsInStringLongestFirst(s)) {
      final PsiFile[] files = cacheManager.getFilesWithWord(word, UsageSearchContext.ANY, scope, true);
      result.retainAll(Arrays.asList(files));
    }
  }

  private static void findAllStyleApplications(final Project project,
                                               final VirtualFile layoutVFile,
                                               final Map<AndroidAttributeInfo, String> attrMap,
                                               final PsiElement parentStyleNameAttrValue,
                                               final List<UsageInfo> usages) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        final PsiFile layoutFile = PsiManager.getInstance(project).findFile(layoutVFile);

        if (!(layoutFile instanceof XmlFile)) {
          return;
        }
        if (!(DomManager.getDomManager(project).getDomFileDescription(
          (XmlFile)layoutFile) instanceof LayoutDomFileDescription)) {
          return;
        }
        layoutFile.accept(new XmlRecursiveElementVisitor() {
          @Override
          public void visitXmlTag(XmlTag tag) {
            super.visitXmlTag(tag);

            if (isPossibleApplicationOfStyle(project, tag, attrMap, parentStyleNameAttrValue)) {
              usages.add(new UsageInfo(tag));
            }
          }
        });
        PsiManager.getInstance(project).dropResolveCaches();
        InjectedLanguageManager.getInstance(project).dropFileCaches(layoutFile);
      }
    });
  }

  @Nullable
  private static PsiElement getStyleNameAttrValueForTag(@NotNull LayoutViewElement element) {
    final AndroidResourceReferenceBase styleRef = AndroidDomUtil.
      getAndroidResourceReference(element.getStyle(), false);

    if (styleRef != null) {
      final PsiElement[] styleElements = styleRef.computeTargetElements();

      if (styleElements.length == 1) {
        return styleElements[0];
      }
    }
    return null;
  }

  private static boolean isPossibleApplicationOfStyle(Project project,
                                                      XmlTag candidate,
                                                      Map<AndroidAttributeInfo, String> attrMap,
                                                      PsiElement parentStyleNameAttrValue) {
    final DomElement domCandidate = DomManager.getDomManager(project).getDomElement(candidate);

    if (!(domCandidate instanceof LayoutViewElement)) {
      return false;
    }
    final LayoutViewElement candidateView = (LayoutViewElement)domCandidate;
    final Map<Pair<String, String>, String> attrsInCandidateMap = new HashMap<Pair<String, String>, String>();
    final List<XmlAttribute> attrsInCandidate = AndroidExtractStyleAction.getExtractableAttributes(candidate);

    if (attrsInCandidate.size() < attrMap.size()) {
      return false;
    }

    for (XmlAttribute attribute : attrsInCandidate) {
      final String attrValue = attribute.getValue();

      if (attrValue != null) {
        attrsInCandidateMap.put(Pair.create(attribute.getNamespace(), attribute.getLocalName()), attrValue);
      }
    }

    for (Map.Entry<AndroidAttributeInfo, String> entry : attrMap.entrySet()) {
      final String ns = entry.getKey().getNamespace();
      final String name = entry.getKey().getName();
      final String value = entry.getValue();
      final String valueInCandidate = attrsInCandidateMap.get(Pair.create(ns, name));

      if (valueInCandidate == null || !valueInCandidate.equals(value)) {
        return false;
      }
    }

    if (candidateView.getStyle().getStringValue() != null) {
      if (parentStyleNameAttrValue == null) {
        return false;
      }
      final PsiElement styleNameAttrValueForTag = getStyleNameAttrValueForTag(candidateView);

      if (styleNameAttrValueForTag == null ||
          !parentStyleNameAttrValue.equals(styleNameAttrValueForTag)) {
        return false;
      }
    }
    else if (parentStyleNameAttrValue != null) {
      return false;
    }
    return true;
  }


  public void setSearchOnlyInCurrentModule(boolean searchOnlyInCurrentModule) {
    mySearchOnlyInCurrentModule = searchOnlyInCurrentModule;
  }

  public void setFileToScan(VirtualFile fileToScan) {
    myFileToScan = fileToScan;
  }

  @NotNull
  public Module getModule() {
    return myModule;
  }

  @NotNull
  public String getStyleName() {
    return myStyleName;
  }

  public void configureScope(MyScope scope, @Nullable VirtualFile context) {
    if (scope == MyScope.MODULE) {
      setSearchOnlyInCurrentModule(true);
    }
    else if (scope == MyScope.FILE) {
      setSearchOnlyInCurrentModule(true);
      setFileToScan(context);
    }
  }

  enum MyScope {
    PROJECT, MODULE, FILE
  }
}
