// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.references;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.find.FindModel;
import com.intellij.find.impl.FindInProjectUtil;
import com.intellij.ide.presentation.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PsiJavaElementPattern;
import com.intellij.patterns.PsiMethodPattern;
import com.intellij.patterns.XmlPatterns;
import com.intellij.patterns.uast.UastPatterns;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceUtil;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.PsiFileReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.usages.FindUsagesProcessPresentation;
import com.intellij.usages.UsageViewPresentation;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.util.PsiUtil;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UastContextKt;
import org.jetbrains.uast.UastLiteralUtils;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import static com.intellij.patterns.PsiJavaPatterns.*;

public class IconsReferencesContributor extends PsiReferenceContributor
  implements QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {

  @Override
  public boolean execute(@NotNull ReferencesSearch.SearchParameters queryParameters, @NotNull final Processor<? super PsiReference> consumer) {
    final PsiElement file = queryParameters.getElementToSearch();
    if (file instanceof PsiBinaryFile) {
      final Module module = ReadAction.compute(() -> ModuleUtilCore.findModuleForPsiElement(file));

      final VirtualFile image = ((PsiBinaryFile)file).getVirtualFile();
      if (isImage(image) && isIconsModule(module)) {
        final Project project = file.getProject();
        final FindModel model = new FindModel();
        final String path = getPathToImage(image, module);
        model.setStringToFind(path);
        model.setCaseSensitive(true);
        model.setFindAll(true);
        model.setWholeWordsOnly(true);
        FindInProjectUtil.findUsages(model, project, usage -> {
          ApplicationManager.getApplication().runReadAction(() -> {
            final PsiElement element = usage.getElement();

            final ProperTextRange textRange = usage.getRangeInElement();
            if (element != null && textRange != null) {
              final PsiElement start = element.findElementAt(textRange.getStartOffset());
              final PsiElement end = element.findElementAt(textRange.getEndOffset());
              if (start != null && end != null) {
                PsiElement value = PsiTreeUtil.findCommonParent(start, end);
                if (value instanceof PsiJavaToken) {
                  value = value.getParent();
                }
                if (value != null) {
                  final PsiFileReference reference = FileReferenceUtil.findFileReference(value);
                  if (reference != null) {
                    consumer.process(reference);
                  }
                }
              }
            }
          });
          return true;
        }, new FindUsagesProcessPresentation(new UsageViewPresentation()));
      }
    }
    return true;
  }

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registerForPresentationAnnotation(registrar);
    registerForIconLoaderMethods(registrar);
    registerForIconXmlAttribute(registrar);
  }

  private static void registerForIconXmlAttribute(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue().withLocalName("icon"), new PsiReferenceProvider() {
      @Override
      public PsiReference @NotNull [] getReferencesByElement(@NotNull final PsiElement element, @NotNull ProcessingContext context) {
        if (!PsiUtil.isPluginXmlPsiElement(element)) {
          return PsiReference.EMPTY_ARRAY;
        }

        return new PsiReference[]{
          new IconPsiReferenceBase(element) {
            @Override
            public PsiElement resolve() {
              String value = ((XmlAttributeValue)element).getValue();
              if (value.startsWith("/")) {
                FileReference lastRef = new FileReferenceSet(element).getLastReference();
                return lastRef != null ? lastRef.resolve() : null;
              }

              return resolveIconPath(value, element);
            }

            @Override
            public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
              PsiElement element = resolve();
              PsiElement resultForFile = handleFile(element, lastRef -> lastRef.handleElementRename(newElementName));
              if (resultForFile != null) {
                return resultForFile;
              }

              PsiElement resultForField = handleField(element, newElementName);
              if (resultForField != null) {
                return resultForField;
              }

              return super.handleElementRename(newElementName);
            }

            @Override
            public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
              PsiElement resultForFile = handleFile(element, lastRef -> lastRef.bindToElement(element));
              if (resultForFile != null) {
                return resultForFile;
              }

              PsiElement resultForField = handleField(element, null);
              if (resultForField != null) {
                return resultForField;
              }

              return super.bindToElement(element);
            }

            private PsiElement handleFile(PsiElement element, Function<FileReference, PsiElement> callback) {
              if (element instanceof PsiFile) {
                FileReference lastRef = new FileReferenceSet(element).getLastReference();
                if (lastRef != null) {
                  return callback.apply(lastRef);
                }
              }
              return null;
            }

            @Nullable
            private PsiElement handleField(PsiElement element, @Nullable String newElementName) {
              if (element instanceof PsiField) {
                PsiClass containingClass = ((PsiField)element).getContainingClass();
                if (containingClass != null) {
                  String classQualifiedName = containingClass.getQualifiedName();
                  if (classQualifiedName != null) {
                    if (newElementName == null) {
                      newElementName = ((PsiField)element).getName();
                    }
                    if (classQualifiedName.startsWith("com.intellij.icons.")) {
                      return replace(classQualifiedName, newElementName, "com.intellij.icons.");
                    }
                    if (classQualifiedName.startsWith("icons.")) {
                      return replace(classQualifiedName, newElementName, "icons.");
                    }
                  }
                }
              }
              return null;
            }

            private PsiElement replace(String fqn, String newName, String pckg) {
              XmlAttribute parent = (XmlAttribute)getElement().getParent();
              parent.setValue(fqn.substring(pckg.length()) + "." + newName);
              return parent.getValueElement();
            }
          }
        };
      }
    });
  }

  private static void registerForIconLoaderMethods(@NotNull PsiReferenceRegistrar registrar) {
    final PsiMethodPattern method = psiMethod().withName("findIcon", "getIcon").definedInClass(IconLoader.class.getName());
    final PsiJavaElementPattern.Capture<PsiLiteralExpression> findGetIconPattern
      = literalExpression().and(psiExpression().methodCallParameter(0, method));
    registrar.registerReferenceProvider(findGetIconPattern, new PsiReferenceProvider() {
      @Override
      public PsiReference @NotNull [] getReferencesByElement(@NotNull final PsiElement element, @NotNull ProcessingContext context) {
        if (!PsiUtil.isIdeaProject(element.getProject())) return PsiReference.EMPTY_ARRAY;
        return new FileReferenceSet(element) {
          @Override
          protected Collection<PsiFileSystemItem> getExtraContexts() {
            Module iconsModule = ModuleManager.getInstance(element.getProject()).findModuleByName("intellij.platform.icons");
            if (iconsModule == null) {
              iconsModule = ModuleManager.getInstance(element.getProject()).findModuleByName("icons");
            }
            if (iconsModule == null) {
              return super.getExtraContexts();
            }

            final List<PsiFileSystemItem> result = new SmartList<>();
            final VirtualFile[] roots = ModuleRootManager.getInstance(iconsModule).getSourceRoots();
            final PsiManager psiManager = element.getManager();
            for (VirtualFile root : roots) {
              final PsiDirectory directory = psiManager.findDirectory(root);
              ContainerUtil.addIfNotNull(result, directory);
            }
            return result;
          }
        }.getAllReferences();
      }
    }, PsiReferenceRegistrar.HIGHER_PRIORITY);
  }

  private static void registerForPresentationAnnotation(@NotNull PsiReferenceRegistrar registrar) {
    UastReferenceRegistrar.registerUastReferenceProvider(
      registrar,
      UastPatterns.injectionHostUExpression()
        .sourcePsiFilter(psi -> PsiUtil.isPluginProject(psi.getProject()))
        .annotationParam(Presentation.class.getName(), "icon"),
      UastReferenceRegistrar.uastInjectionHostReferenceProvider((uElement, referencePsiElement) -> new PsiReference[]{
        new IconPsiReferenceBase(referencePsiElement) {

          private UElement getUElement() {
            return UastContextKt.toUElement(getElement());
          }

          @Override
          public PsiElement resolve() {
            final UElement uElement = getUElement();
            if (uElement == null) return null;

            String value = UastLiteralUtils.getValueIfStringLiteral(uElement);
            return resolveIconPath(value, getElement());
          }

          @Override
          public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
            PsiElement field = resolve();
            PsiElement result = handleElement(field, newElementName);
            if (result != null) {
              return result;
            }
            return super.handleElementRename(newElementName);
          }

          @Nullable
          private PsiElement handleElement(PsiElement element, @Nullable String newElementName) {
            if (element instanceof PsiField) {
              PsiClass containingClass = ((PsiField)element).getContainingClass();
              if (containingClass != null) {
                String classQualifiedName = containingClass.getQualifiedName();
                if (classQualifiedName != null) {
                  if (newElementName == null) {
                    newElementName = ((PsiField)element).getName();
                  }
                  if (classQualifiedName.startsWith("com.intellij.icons.")) {
                    return replace(newElementName, classQualifiedName, "com.intellij.icons.");
                  }
                  if (classQualifiedName.startsWith("icons.")) {
                    return replace(newElementName, classQualifiedName, "icons.");
                  }
                }
              }
            }
            return null;
          }

          private PsiElement replace(String newElementName, String fqn, String packageName) {
            String newValue = fqn.substring(packageName.length()) + "." + newElementName;
            return ElementManipulators.handleContentChange(getElement(), newValue);
          }
        }
      }), PsiReferenceRegistrar.HIGHER_PRIORITY);
  }


  @NotNull
  private static String getPathToImage(VirtualFile image, Module module) {
    final String path = ModuleRootManager.getInstance(module).getSourceRoots()[0].getPath();
    return "/" + FileUtil.getRelativePath(path, image.getPath(), '/');
  }

  private static boolean isIconsModule(Module module) {
    return module != null && ("icons".equals(module.getName()) || "intellij.platform.icons".equals(module.getName()))
           && ModuleRootManager.getInstance(module).getSourceRoots().length == 1;
  }

  private static boolean isImage(VirtualFile image) {
    final FileTypeManager mgr = FileTypeManager.getInstance();
    return image != null && mgr.getFileTypeByFile(image) == mgr.getFileTypeByExtension("png");
  }

  @Nullable
  private static PsiField resolveIconPath(String pathStr, PsiElement element) {
    if (pathStr == null) {
      return null;
    }

    List<String> path = StringUtil.split(pathStr, ".");
    if (path.size() > 1 && path.get(0).endsWith("Icons")) {
      Project project = element.getProject();
      PsiClass cur = findIconClass(project, path.get(0));
      if (cur == null) {
        return null;
      }

      for (int i = 1; i < path.size() - 1; i++) {
        cur = cur.findInnerClassByName(path.get(i), false);
        if (cur == null) {
          return null;
        }
      }

      return cur.findFieldByName(path.get(path.size() - 1), false);
    }

    return null;
  }

  @Nullable
  private static PsiClass findIconClass(Project project, String className) {
    final boolean isAllIcons = "AllIcons".equals(className);
    final String fqnClassName = isAllIcons ? "com.intellij.icons.AllIcons" : "icons." + className;
    return JavaPsiFacade.getInstance(project)
      .findClass(fqnClassName, isAllIcons ? GlobalSearchScope.allScope(project) : GlobalSearchScope.projectScope(project));
  }

  private static abstract class IconPsiReferenceBase extends PsiReferenceBase<PsiElement> implements EmptyResolveMessageProvider {
    IconPsiReferenceBase(@NotNull PsiElement element) {
      super(element, true);
    }

    @SuppressWarnings("UnresolvedPropertyKey")
    @NotNull
    @Override
    public String getUnresolvedMessagePattern() {
      return DevKitBundle.message("inspections.presentation.cannot.resolve.icon");
    }
  }
}
