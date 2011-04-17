/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.android.dom.converters;

import com.intellij.codeInsight.completion.JavaLookupElementBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import com.intellij.util.xml.*;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class PackageClassConverter extends ResolvingConverter<PsiClass> implements CustomReferenceConverter<PsiClass> {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.dom.converters.PackageClassConverter");

  private final String myExtendClassName;

  public PackageClassConverter(String extendClassName) {
    myExtendClassName = extendClassName;
  }

  public PackageClassConverter() {
    myExtendClassName = null;
  }

  @NotNull
  public Collection<? extends PsiClass> getVariants(ConvertContext context) {
    return Collections.emptyList();
  }

  public PsiClass fromString(@Nullable @NonNls String s, ConvertContext context) {
    if (s == null) return null;
    DomElement domElement = context.getInvocationElement();
    Manifest manifest = domElement.getParentOfType(Manifest.class, true);
    s = s.replace('$', '.');
    String packageName = manifest != null ? manifest.getPackage().getValue() : null;
    String className = null;

    if (packageName != null) {
      if (s.startsWith(".")) {
        className = packageName + s;
      }
      else {
        className = packageName + "." + s;
      }
    }
    JavaPsiFacade facade = JavaPsiFacade.getInstance(context.getPsiManager().getProject());
    final Module module = context.getModule();
    GlobalSearchScope scope = module != null
                              ? GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module)
                              : context.getInvocationElement().getResolveScope();
    PsiClass psiClass = className != null ? facade.findClass(className, scope) : null;
    if (psiClass == null) {
      psiClass = facade.findClass(s, scope);
    }
    return psiClass;
  }

  @NotNull
  @Override
  public PsiReference[] createReferences(GenericDomValue<PsiClass> value, PsiElement element, ConvertContext context) {
    assert element instanceof XmlAttributeValue;
    final XmlAttributeValue attrValue = (XmlAttributeValue)element;
    final String strValue = attrValue.getValue();

    final boolean startsWithPoint = strValue.startsWith(".");
    final int start = attrValue.getValueTextRange().getStartOffset() - attrValue.getTextRange().getStartOffset();

    final DomElement domElement = context.getInvocationElement();
    final Manifest manifest = domElement.getParentOfType(Manifest.class, true);
    final String basePackage = manifest == null ? null : manifest.getPackage().getValue();
    final ExtendClass extendClassAnnotation = domElement.getAnnotation(ExtendClass.class);
    final String extendsClassName = extendClassAnnotation != null ? extendClassAnnotation.value() : myExtendClassName;
    final boolean inModuleOnly = domElement.getAnnotation(CompleteNonModuleClass.class) == null;

    List<PsiReference> result = new ArrayList<PsiReference>();
    final String[] nameParts = strValue.split("\\.");
    if (nameParts.length == 0) {
      return PsiReference.EMPTY_ARRAY;
    }

    final Module module = context.getModule();
    int offset = start;

    for (int i = 0, n = nameParts.length; i < n - 1; i++) {
      final String packageName = nameParts[i];

      if (packageName.length() > 0) {
        offset += packageName.length();
        final TextRange range = new TextRange(offset - packageName.length(), offset);
        result.add(new MyReference(element, range, basePackage, startsWithPoint, start, true, module, extendsClassName, inModuleOnly));
      }
      offset++;
    }

    final String className = nameParts[nameParts.length - 1];
    final String[] classNameParts = className.split("\\$");

    for (String s : classNameParts) {
      if (s.length() > 0) {
        offset += s.length();

        final TextRange range = new TextRange(offset - s.length(), offset);
        result.add(new MyReference(element, range, basePackage, startsWithPoint, start, false, module, extendsClassName, inModuleOnly));
      }
      offset++;
    }

    return result.toArray(new PsiReference[result.size()]);
  }

  @Nullable
  private static String getQualifiedName(@NotNull PsiClass aClass) {
    PsiElement parent = aClass.getParent();
    if (parent instanceof PsiClass) {
      String parentQName = getQualifiedName((PsiClass)parent);
      if (parentQName == null) return null;
      return parentQName + "$" + aClass.getName();
    }
    return aClass.getQualifiedName();
  }

  @Nullable
  private static String getName(@NotNull PsiClass aClass) {
    PsiElement parent = aClass.getParent();
    if (parent instanceof PsiClass) {
      String parentName = getName((PsiClass)parent);
      if (parentName == null) return null;
      return parentName + "$" + aClass.getName();
    }
    return aClass.getName();
  }

  @Nullable
  public String toString(@Nullable PsiClass psiClass, ConvertContext context) {
    DomElement domElement = context.getInvocationElement();
    Manifest manifest = domElement.getParentOfType(Manifest.class, true);
    final String packageName = manifest == null ? null : manifest.getPackage().getValue();
    return classToString(psiClass, packageName, "");
  }

  @Nullable
  private static String classToString(PsiClass psiClass, String basePackageName, String prefix) {
    if (psiClass == null) return null;
    String qName = getQualifiedName(psiClass);
    if (qName == null) return null;
    PsiFile file = psiClass.getContainingFile();
    if (file instanceof PsiClassOwner) {
      PsiClassOwner psiFile = (PsiClassOwner)file;
      if (Comparing.equal(psiFile.getPackageName(), basePackageName)) {
        String name = getName(psiClass);
        if (name != null) {
          final String dottedName = '.' + name;
          if (dottedName.startsWith(prefix)) {
            return dottedName;
          }
          else if (name.startsWith(prefix)) {
            return name;
          }
        }
      }
      else if (basePackageName != null && qName.startsWith(basePackageName)) {
        final String name = qName.substring(basePackageName.length());
        if (name.startsWith(prefix)) {
          return name;
        }
      }
    }
    return qName;
  }

  @NotNull
  public static Collection<PsiClass> findInheritors(@NotNull final Module module, @NotNull final String className, boolean inModuleOnly) {
    final Project project = module.getProject();
    PsiClass base = JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project));
    if (base != null) {
      GlobalSearchScope scope = inModuleOnly ? GlobalSearchScope.moduleWithDependenciesScope(module) : GlobalSearchScope.allScope(project);
      Query<PsiClass> query = ClassInheritorsSearch.search(base, scope, true);
      return query.findAll();
    }
    return new ArrayList<PsiClass>();
  }

  private static class MyReference extends PsiReferenceBase<PsiElement> {
    private final int myStart;
    private final String myBasePackage;
    private final boolean myStartsWithPoint;
    private final boolean myIsPackage;
    private final Module myModule;
    private final String myExtendsClass;
    private final boolean myCompleteOnlyModuleClasses;

    public MyReference(PsiElement element,
                       TextRange range,
                       String basePackage,
                       boolean startsWithPoint,
                       int start,
                       boolean isPackage,
                       Module module,
                       String extendsClass,
                       boolean completeOnlyModuleClasses) {
      super(element, range, true);
      myBasePackage = basePackage;
      myStartsWithPoint = startsWithPoint;
      myStart = start;
      myIsPackage = isPackage;
      myModule = module;
      myExtendsClass = extendsClass;
      myCompleteOnlyModuleClasses = completeOnlyModuleClasses;
    }

    @Override
    public PsiElement resolve() {
      final PsiManager manager = getElement().getManager();
      if (manager instanceof PsiManagerImpl) {
        return ((PsiManagerImpl)manager).getResolveCache().resolveWithCaching(this, new ResolveCache.Resolver() {
            @Nullable
            @Override
            public PsiElement resolve(PsiReference reference, boolean incompleteCode) {
              return resolveInner();
            }
          }, false, false);
      }
      return resolveInner();
    }

    @Nullable
    private PsiElement resolveInner() {
      final int end = getRangeInElement().getEndOffset();
      final String value = myElement.getText().substring(myStart, end).replace('$', '.');
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(myElement.getProject());

      if (!myStartsWithPoint) {
        final PsiElement element = myIsPackage ?
                                   facade.findPackage(value) :
                                   facade.findClass(value, myModule != null
                                                           ? myModule.getModuleWithDependenciesScope()
                                                           : myElement.getResolveScope());

        if (element != null) {
          return element;
        }
      }

      final String relativeName = getRelativeName(value);
      if (relativeName != null) {
        return myIsPackage ?
               facade.findPackage(relativeName) :
               facade.findClass(relativeName, myModule != null
                                              ? myModule.getModuleWithDependenciesScope()
                                              : myElement.getResolveScope());
      }
      return null;
    }

    @Nullable
    private String getRelativeName(String value) {
      if (myBasePackage == null) {
        return null;
      }
      return myBasePackage + (myStartsWithPoint ? "" : ".") + value;
    }

    @NotNull
    @Override
    public Object[] getVariants() {
      if (myExtendsClass != null) {
        final List<PsiClass> classes = new ArrayList<PsiClass>();
        classes.addAll(findInheritors(myModule, myExtendsClass, myCompleteOnlyModuleClasses));

        final List<Object> result = new ArrayList<Object>(classes.size());

        for (int i = 0, n = classes.size(); i < n; i++) {
          final PsiClass psiClass = classes.get(i);
          final String prefix = myElement.getText().substring(myStart, getRangeInElement().getStartOffset());
          String name = classToString(psiClass, myBasePackage, prefix);

          if (name != null && name.startsWith(prefix)) {
            name = name.substring(prefix.length());
            result.add(JavaLookupElementBuilder.forClass(psiClass, name, true));
          }
        }
        return ArrayUtil.toObjectArray(result);
      }
      return EMPTY_ARRAY;
    }

    @Override
    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
      if (element instanceof PsiClass || element instanceof PsiPackage) {
        final String newName = element instanceof PsiClass ? classToString((PsiClass)element, myBasePackage, "") :
                               packageToString((PsiPackage)element, myBasePackage);
        assert newName != null;

        final ElementManipulator<PsiElement> manipulator = ElementManipulators.getManipulator(myElement);
        final TextRange range = new TextRange(myStart, getRangeInElement().getEndOffset());
        return manipulator != null ? manipulator.handleContentChange(getElement(), range, newName) : element;
      }
      LOG.error("PackageClassConverter resolved to " + element.getClass());
      return super.bindToElement(element);
    }

    private static String packageToString(PsiPackage psiPackage, String basePackageName) {
      final String qName = psiPackage.getQualifiedName();
      return basePackageName != null && qName.startsWith(basePackageName) ?
             qName.substring(basePackageName.length()) :
             qName;
    }
  }
}
