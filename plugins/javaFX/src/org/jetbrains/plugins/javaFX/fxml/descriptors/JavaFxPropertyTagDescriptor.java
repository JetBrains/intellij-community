package org.jetbrains.plugins.javaFX.fxml.descriptors;

import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlElementsGroup;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.schema.AnyXmlAttributeDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * User: anna
 * Date: 1/10/13
 */
public class JavaFxPropertyTagDescriptor implements XmlElementDescriptor {
  private final PsiClass myPsiClass;
  private final String myName;
  private final boolean myStatic;

  public JavaFxPropertyTagDescriptor(PsiClass psiClass, String name, boolean isStatic) {
    myPsiClass = psiClass;
    myName = name;
    myStatic = isStatic;
  }

  public PsiClass getPsiClass() {
    return myPsiClass;
  }

  public boolean isStatic() {
    return myStatic;
  }

  @Override
  public String getQualifiedName() {
    return getName();
  }

  @Override
  public String getDefaultName() {
    return getName();
  }

  @Override
  public XmlElementDescriptor[] getElementsDescriptors(XmlTag context) {
    final PsiElement declaration = getDeclaration();
    final PsiType propertyType = JavaFxPsiUtil.getWritablePropertyType(myPsiClass, declaration);

    if (propertyType != null) {
      final ArrayList<XmlElementDescriptor> descriptors = new ArrayList<>();
      for (String name : FxmlConstants.FX_BUILT_IN_TAGS) {
        descriptors.add(new JavaFxBuiltInTagDescriptor(name, null));
      }

      final PsiType collectionItemType = JavaGenericsUtil.getCollectionItemType(propertyType, declaration.getResolveScope());
      if (collectionItemType != null) {
        collectSubclassesDescriptors(collectionItemType, descriptors, context);
      }
      else if (!JavaFxPsiUtil.isPrimitiveOrBoxed(propertyType)) {
        collectSubclassesDescriptors(propertyType, descriptors, context);
      }

      if (!descriptors.isEmpty()) return descriptors.toArray(XmlElementDescriptor.EMPTY_ARRAY);
    }
    return XmlElementDescriptor.EMPTY_ARRAY;
  }

  private static void collectSubclassesDescriptors(@Nullable PsiType psiType,
                                                   @NotNull final List<XmlElementDescriptor> descriptors,
                                                   @NotNull PsiElement context) {
    final PsiClass aClass = PsiUtil.resolveClassInType(psiType);
    if (aClass != null) {
      if (CommonClassNames.JAVA_LANG_OBJECT.equals(aClass.getQualifiedName())) {
        collectRawPropertyDescriptors(descriptors, context);
        return;
      }
      ClassInheritorsSearch.search(aClass, aClass.getUseScope(), true, true, false)
        .forEach(psiClass -> {
          addElementDescriptor(descriptors, psiClass);
          return true;
        });
      addElementDescriptor(descriptors, aClass);
    }
  }

  private static void collectRawPropertyDescriptors(@NotNull List<XmlElementDescriptor> descriptors, @NotNull PsiElement context) {
    final Project project = context.getProject();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    // Offer most used simple types. TODO try to guess suitable types from the project sources
    addElementDescriptor(descriptors, facade.findClass(CommonClassNames.JAVA_LANG_STRING, scope));
    addElementDescriptor(descriptors, facade.findClass(CommonClassNames.JAVA_LANG_DOUBLE, scope));
    addElementDescriptor(descriptors, facade.findClass(CommonClassNames.JAVA_LANG_INTEGER, scope));
    addElementDescriptor(descriptors, facade.findClass(CommonClassNames.JAVA_LANG_BOOLEAN, scope));
  }

  private static void addElementDescriptor(@NotNull List<XmlElementDescriptor> descriptors, @Nullable PsiClass aClass) {
    if (aClass != null &&
        !CommonClassNames.JAVA_LANG_OBJECT.equals(aClass.getQualifiedName()) &&
        !aClass.isInterface() &&
        !PsiUtil.isAbstractClass(aClass) &&
        !PsiUtil.isInnerClass(aClass) &&
        JavaFxPsiUtil.isAbleToInstantiate(aClass)) {
      descriptors.add(new JavaFxClassTagDescriptor(aClass.getName(), aClass));
    }
  }

  @Nullable
  @Override
  public XmlElementDescriptor getElementDescriptor(XmlTag childTag, XmlTag contextTag) {
    return JavaFxClassTagDescriptorBase.createTagDescriptor(childTag);
  }

  @Override
  public XmlAttributeDescriptor[] getAttributesDescriptors(@Nullable XmlTag context) {
    return XmlAttributeDescriptor.EMPTY;
  }

  @Nullable
  @Override
  public XmlAttributeDescriptor getAttributeDescriptor(@NonNls String attributeName, @Nullable XmlTag context) {
    final PsiElement declaration = getDeclaration();
    final PsiType propertyType = JavaFxPsiUtil.getWritablePropertyType(myPsiClass, declaration);
    if (InheritanceUtil.isInheritor(propertyType, CommonClassNames.JAVA_UTIL_MAP)) {
      return new AnyXmlAttributeDescriptor(attributeName);
    }
    return null;
  }

  @Nullable
  @Override
  public XmlAttributeDescriptor getAttributeDescriptor(XmlAttribute attribute) {
    return getAttributeDescriptor(attribute.getName(), attribute.getParent());
  }

  @Override
  public XmlNSDescriptor getNSDescriptor() {
    return null;
  }

  @Nullable
  @Override
  public XmlElementsGroup getTopGroup() {
    return null;
  }

  @Override
  public int getContentType() {
    return CONTENT_TYPE_UNKNOWN;
  }

  @Nullable
  @Override
  public String getDefaultValue() {
    return null;
  }

  @Override
  public PsiElement getDeclaration() {
    if (myPsiClass == null) return null;
    if (myStatic) return JavaFxPsiUtil.findStaticPropertySetter(myName, myPsiClass);
    return JavaFxPsiUtil.collectWritableProperties(myPsiClass).get(myName);
  }

  @Override
  public String getName(PsiElement context) {
    return getName();
  }

  @Override
  public String getName() {
    if (myPsiClass != null && myStatic) {
      return StringUtil.getQualifiedName(myPsiClass.getName(), myName);
    }
    return myName;
  }

  @Override
  public void init(PsiElement element) {}

  @Override
  public Object[] getDependences() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public String toString() {
    return "<" + (myStatic ? "static " : "") + (myPsiClass != null ? myPsiClass.getName() + "#" : "?#") + myName + ">";
  }
}
