package org.jetbrains.android.inspections.lint;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiBinaryFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class AndroidLintInspectionBase extends GlobalInspectionTool {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.inspections.lint.AndroidLintInspectionBase");

  private static final Map<Issue, String> ourIssue2InspectionShortName = new HashMap<Issue, String>();

  protected final Issue myIssue;
  private final String[] myGroupPath;
  private final String myDisplayName;

  protected AndroidLintInspectionBase(@NotNull String displayName, @NotNull Issue issue) {
    myIssue = issue;

    final Category category = issue.getCategory();
    final String[] categoryNames = category != null
                                   ? computeAllNames(category)
                                   : ArrayUtil.EMPTY_STRING_ARRAY;

    myGroupPath = ArrayUtil.mergeArrays(new String[]{AndroidBundle.message("android.inspections.group.name"),
      AndroidBundle.message("android.lint.inspections.subgroup.name")}, categoryNames);
    myDisplayName = displayName;

    addIssue(issue, getShortName());
  }

  @NotNull
  protected AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
    return AndroidLintQuickFix.EMPTY_ARRAY;
  }

  @NotNull
  protected IntentionAction[] getIntentions(@NotNull PsiElement startElement, @NotNull PsiElement endElement) {
    return IntentionAction.EMPTY_ARRAY;
  }
  
  @NotNull
  private LocalQuickFix[] getLocalQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
    final AndroidLintQuickFix[] fixes = getQuickFixes(message);
    final LocalQuickFix[] result = new LocalQuickFix[fixes.length];
    
    for (int i = 0; i < fixes.length; i++) {
      if (fixes[i].isApplicable(startElement, endElement, true)) {
        result[i] = new MyLocalQuickFix(fixes[i]);
      }
    }
    return result;
  }

  @Override
  public void runInspection(AnalysisScope scope,
                            final InspectionManager manager,
                            final GlobalInspectionContext globalContext,
                            final ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    final AndroidLintGlobalInspectionContext androidLintContext = globalContext.getExtension(AndroidLintGlobalInspectionContext.ID);
    if (androidLintContext == null) {
      return;
    }

    final Map<Issue, Map<File, List<ProblemData>>> problemMap = androidLintContext.getResults();
    if (problemMap == null) {
      return;
    }

    final Map<File, List<ProblemData>> file2ProblemList = problemMap.get(myIssue);
    if (file2ProblemList == null) {
      return;
    }

    for (final Map.Entry<File, List<ProblemData>> entry : file2ProblemList.entrySet()) {
      final File file = entry.getKey();
      final VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(file);

      if (vFile == null) {
        continue;
      }
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        @Override
        public void run() {
          final PsiManager psiManager = PsiManager.getInstance(globalContext.getProject());
          final PsiFile psiFile = psiManager.findFile(vFile);

          if (psiFile != null) {
            final ProblemDescriptor[] descriptors = computeProblemDescriptors(psiFile, manager, entry.getValue());

            if (descriptors.length > 0) {
              problemDescriptionsProcessor.addProblemElement(globalContext.getRefManager().getReference(psiFile), descriptors);
            }
          }
        }
      });
    }
  }

  @NotNull
  private ProblemDescriptor[] computeProblemDescriptors(@NotNull PsiFile psiFile,
                                                        @NotNull InspectionManager manager,
                                                        @NotNull List<ProblemData> problems) {
    final List<ProblemDescriptor> result = new ArrayList<ProblemDescriptor>();

    for (ProblemData problemData : problems) {
      final String s = problemData.getMessage();
      final String message = XmlUtil.escape(s.indexOf('>') >= 0 && s.indexOf('<') >= 0 ? s.replace('<', '{').replace('>', '}') : s);
      final TextRange range = problemData.getTextRange();

      if (range.getStartOffset() == range.getEndOffset()) {
        PsiFile f = psiFile;

        if (f instanceof PsiBinaryFile) {
          // todo: show inspection in binary file (fix NPE)!
          final Module module = ModuleUtil.findModuleForPsiElement(f);

          if (module != null) {
            final AndroidFacet facet = AndroidFacet.getInstance(module);
            final VirtualFile manifestFile = facet != null ? AndroidRootUtil.getManifestFile(facet) : null;

            if (manifestFile != null) {
              f = f.getManager().findFile(manifestFile);
            }
          }
        }

        if (f != null) {
          result.add(manager.createProblemDescriptor(f, message, false, getLocalQuickFixes(f, f, message),
                                                     ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
        }
      }
      else {
        final PsiElement startElement = psiFile.findElementAt(range.getStartOffset());
        final PsiElement endElement = psiFile.findElementAt(range.getEndOffset() - 1);

        if (startElement != null && endElement != null) {
          result.add(manager.createProblemDescriptor(startElement, endElement, message,
                                                     ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false,
                                                     getLocalQuickFixes(startElement, endElement, message)));
        }
      }
    }
    return result.toArray(new ProblemDescriptor[result.size()]);
  }

  private synchronized static void addIssue(@NotNull Issue issue, @NotNull String shortName) {
    ourIssue2InspectionShortName.put(issue, shortName);
  }

  public synchronized static String getInspectionShortNameByIssue(@NotNull Issue issue) {
    return ourIssue2InspectionShortName.get(issue);
  }

  @NotNull
  private static String[] computeAllNames(@NotNull Category category) {
    final List<String> result = new ArrayList<String>();

    Category c = category;

    while (c != null) {
      final String name = c.getName();

      if (name == null) {
        return ArrayUtil.EMPTY_STRING_ARRAY;
      }
      result.add(name);
      c = c.getParent();
    }
    return ArrayUtil.reverseArray(ArrayUtil.toStringArray(result));
  }

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return AndroidBundle.message("android.lint.inspections.group.name");
  }

  @NotNull
  @Override
  public String[] getGroupPath() {
    return myGroupPath;
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return myDisplayName;
  }

  @SuppressWarnings("deprecation")
  @Override
  public String getStaticDescription() {
    String description = myIssue.getDescription();
    String explanation = myIssue.getExplanation();

    description = description != null ? StringUtil.escapeXml(description) : null;
    explanation = explanation != null ? StringUtil.escapeXml(explanation) : null;

    if (description == null && explanation == null) {
      return "";
    }
    else if (description != null && explanation != null) {
      return "<html><body>" + description + "<br><br>" + explanation + "</body></html>";
    }
    else if (description != null) {
      return "<html><body>" + description + "</body></html>";
    }
    else {
      return "<html><body>" + explanation + "</body></html>";
    }
  }

  @Override
  public boolean isEnabledByDefault() {
    return myIssue.isEnabledByDefault();
  }

  @NotNull
  @Override
  public String getShortName() {
    return StringUtil.trimEnd(getClass().getSimpleName(), "Inspection");
  }

  @NotNull
  @Override
  public HighlightDisplayLevel getDefaultLevel() {
    final Severity defaultSeverity = myIssue.getDefaultSeverity();
    if (defaultSeverity == null) {
      return HighlightDisplayLevel.WARNING;
    }
    final HighlightDisplayLevel displayLevel = toHighlightDisplayLevel(defaultSeverity);
    return displayLevel != null ? displayLevel : HighlightDisplayLevel.WARNING;
  }

  @Nullable
  private static HighlightDisplayLevel toHighlightDisplayLevel(@NotNull Severity severity) {
    switch (severity) {
      case ERROR:
        return HighlightDisplayLevel.ERROR;
      case FATAL:
        return HighlightDisplayLevel.ERROR;
      case WARNING:
        return HighlightDisplayLevel.WARNING;
      case INFORMATIONAL:
        return HighlightDisplayLevel.WEAK_WARNING;
      case IGNORE:
        return null;
      default:
        LOG.error("Unknown severity " + severity);
        return null;
    }
  }

  public boolean worksInBatchModeOnly() {
    final EnumSet<Scope> scopeSet = myIssue.getScope();
    if (scopeSet.size() != 1) {
      return true;
    }
    final Scope scope = scopeSet.iterator().next();
    return scope != Scope.JAVA_FILE &&
           scope != Scope.MANIFEST &&
           scope != Scope.RESOURCE_FILE &&
           scope != Scope.PROGUARD_FILE;
  }

  static class MyLocalQuickFix implements LocalQuickFix {
    private final AndroidLintQuickFix myLintQuickFix;

    MyLocalQuickFix(@NotNull AndroidLintQuickFix lintQuickFix) {
      myLintQuickFix = lintQuickFix;
    }

    @NotNull
    @Override
    public String getName() {
      return myLintQuickFix.getName();
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return AndroidBundle.message("android.lint.quickfixes.family");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      myLintQuickFix.apply(descriptor.getStartElement(), descriptor.getEndElement(), null);
    }
  }
}
