/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.devkit.references;

import com.intellij.find.FindModel;
import com.intellij.find.impl.FindInProjectUtil;
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
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.util.DescriptorUtil;
import org.jetbrains.idea.devkit.util.PsiUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.intellij.patterns.PsiJavaPatterns.*;

public class IconsReferencesContributor extends PsiReferenceContributor
  implements QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {

  @Override
  public boolean execute(@NotNull ReferencesSearch.SearchParameters queryParameters, @NotNull final Processor<PsiReference> consumer) {
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
      @NotNull
      @Override
      public PsiReference[] getReferencesByElement(@NotNull final PsiElement element, @NotNull ProcessingContext context) {
        if (!PsiUtil.isPluginProject(element.getProject()) ||
            !DescriptorUtil.isPluginXml(element.getContainingFile())) {
          return PsiReference.EMPTY_ARRAY;
        }

        return new PsiReference[]{
          new PsiReferenceBase<PsiElement>(element, true) {
            @Override
            public PsiElement resolve() {
              String value = ((XmlAttributeValue)element).getValue();
              if (value != null && value.startsWith("/")) {
                FileReference lastRef = new FileReferenceSet(element).getLastReference();
                return lastRef != null ? lastRef.resolve() : null;
              }

              return resolveIconPath(value, element);
            }

            @Override
            public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
              PsiElement element = resolve();
              if (element instanceof PsiFile) {
                FileReference lastRef = new FileReferenceSet(element).getLastReference();
                if (lastRef != null) {
                  return lastRef.handleElementRename(newElementName);
                }
              }

              if (element instanceof PsiField) {
                PsiClass containingClass = ((PsiField)element).getContainingClass();
                if (containingClass != null) {
                  PsiElement result = handleElement(newElementName, containingClass.getQualifiedName());
                  if (result != null) {
                    return result;
                  }
                }
              }

              return super.handleElementRename(newElementName);
            }

            @Override
            public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
              if (element instanceof PsiFile) {
                FileReference lastRef = new FileReferenceSet(element).getLastReference();
                if (lastRef != null) {
                  return lastRef.bindToElement(element);
                }
              }

              if (element instanceof PsiField) {
                PsiClass containingClass = ((PsiField)element).getContainingClass();
                if (containingClass != null) {
                  PsiElement result = handleElement(((PsiField)element).getName(), containingClass.getQualifiedName());
                  if (result != null) {
                    return result;
                  }
                }
              }

              return super.bindToElement(element);
            }

            @Nullable
            private PsiElement handleElement(String newElementName, String classQualifiedName) {
              if (classQualifiedName != null) {
                if (classQualifiedName.startsWith("com.intellij.icons.")) {
                  return replace(classQualifiedName, newElementName, "com.intellij.icons.");
                }
                if (classQualifiedName.startsWith("icons.")) {
                  return replace(classQualifiedName, newElementName, "icons.");
                }
              }
              return null;
            }

            private PsiElement replace(String fqn, String newName, String pckg) {
              XmlAttribute parent = (XmlAttribute)getElement().getParent();
              parent.setValue(fqn.substring(pckg.length()) + "." + newName);
              return parent.getValueElement();
            }

            @NotNull
            @Override
            public Object[] getVariants() {
              return EMPTY_ARRAY;
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
      @NotNull
      @Override
      public PsiReference[] getReferencesByElement(@NotNull final PsiElement element, @NotNull ProcessingContext context) {
        if (!PsiUtil.isIdeaProject(element.getProject())) return PsiReference.EMPTY_ARRAY;
        return new FileReferenceSet(element) {
          @Override
          protected Collection<PsiFileSystemItem> getExtraContexts() {
            final Module icons = ModuleManager.getInstance(element.getProject()).findModuleByName("icons");
            if (icons != null) {
              final ArrayList<PsiFileSystemItem> result = new ArrayList<>();
              final VirtualFile[] roots = ModuleRootManager.getInstance(icons).getSourceRoots();
              final PsiManager psiManager = element.getManager();
              for (VirtualFile root : roots) {
                final PsiDirectory directory = psiManager.findDirectory(root);
                if (directory != null) {
                  result.add(directory);
                }
              }
              return result;
            }
            return super.getExtraContexts();
          }
        }.getAllReferences();
      }
    });
  }

  private static void registerForPresentationAnnotation(@NotNull PsiReferenceRegistrar registrar) {
    PsiJavaElementPattern.Capture<PsiLiteralExpression> presentationAnnotation
      = literalExpression().annotationParam("com.intellij.ide.presentation.Presentation", "icon");

    registrar.registerReferenceProvider(presentationAnnotation, new PsiReferenceProvider() {
      @NotNull
      @Override
      public PsiReference[] getReferencesByElement(@NotNull final PsiElement element, @NotNull ProcessingContext context) {
        if (!PsiUtil.isPluginProject(element.getProject())) return PsiReference.EMPTY_ARRAY;
        return new PsiReference[]{
          new PsiReferenceBase<PsiElement>(element, true) {
            @Override
            public PsiElement resolve() {
              String value = (String)((PsiLiteralExpression)element).getValue();
              return resolveIconPath(value, element);
            }

            @Override
            public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
              PsiElement field = resolve();
              if (field instanceof PsiField) {
                PsiClass containingClass = ((PsiField)element).getContainingClass();
                if (containingClass != null) {
                  PsiElement result = handleElement(newElementName, containingClass.getQualifiedName(), element);
                  if (result != null) {
                    return result;
                  }
                }
              }

              return super.handleElementRename(newElementName);
            }

            @Override
            public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
              if (element instanceof PsiField) {
                PsiClass containingClass = ((PsiField)element).getContainingClass();
                if (containingClass != null) {
                  PsiElement result = handleElement(((PsiField)element).getName(), containingClass.getQualifiedName(), getElement());
                  if (result != null) {
                    return result;
                  }
                }
              }

              return super.bindToElement(element);
            }

            @Nullable
            private PsiElement handleElement(String newElementName, String classQualifiedName, PsiElement element) {
              if (classQualifiedName != null) {
                if (classQualifiedName.startsWith("com.intellij.icons.")) {
                  return replace(newElementName, classQualifiedName, "com.intellij.icons.", element);
                }
                if (classQualifiedName.startsWith("icons.")) {
                  return replace(newElementName, classQualifiedName, "icons.", element);
                }
              }
              return null;
            }

            private PsiElement replace(String newElementName, String fqn, String pckg, PsiElement container) {
              String newValue = "\"" + fqn.substring(pckg.length()) + "." + newElementName + "\"";
              return getElement().replace(
                JavaPsiFacade.getElementFactory(container.getProject()).createExpressionFromText(newValue, container.getParent()));
            }

            @NotNull
            @Override
            public Object[] getVariants() {
              return EMPTY_ARRAY;
            }
          }
        };
      }
    });
  }


  @NotNull
  private static String getPathToImage(VirtualFile image, Module module) {
    final String path = ModuleRootManager.getInstance(module).getSourceRoots()[0].getPath();
    return "/" + FileUtil.getRelativePath(path, image.getPath(), '/');
  }

  private static boolean isIconsModule(Module module) {
    return module != null && "icons".equals(module.getName())
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
}
