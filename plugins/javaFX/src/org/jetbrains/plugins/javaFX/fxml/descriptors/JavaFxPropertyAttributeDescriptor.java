package org.jetbrains.plugins.javaFX.fxml.descriptors;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.impl.BasicXmlAttributeDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonNames;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * User: anna
 * Date: 1/10/13
 */
public class JavaFxPropertyAttributeDescriptor extends BasicXmlAttributeDescriptor {
  private final String myName;
  private final PsiClass myPsiClass;

  public JavaFxPropertyAttributeDescriptor(String name, PsiClass psiClass) {
    myName = name;
    myPsiClass = psiClass;
  }

  public PsiClass getPsiClass() {
    return myPsiClass;
  }

  @Override
  public boolean isRequired() {
    return false;
  }

  @Override
  public boolean isFixed() {
    return false;
  }

  @Override
  public boolean hasIdType() {
    return false;
  }

  @Override
  public boolean hasIdRefType() {
    return false;
  }

  @Nullable
  @Override
  public String getDefaultValue() {
    return null;
  }

  @Override
  public boolean isEnumerated() {
    return getEnumeratedValues() != null;
  }

  @Nullable
  @Override
  public String[] getEnumeratedValues() {
    final PsiClass aClass = getEnum();
    if (aClass != null) {
      final PsiField[] fields = aClass.getAllFields();
      final List<String> enumConstants = new ArrayList<>();
      for (PsiField enumField : fields) {
        if (isConstant(enumField)) {
          enumConstants.add(enumField.getName());
        }
      }
      return ArrayUtil.toStringArray(enumConstants);
    }

    final String propertyQName = JavaFxPsiUtil.getBoxedPropertyType(myPsiClass, getDeclarationMember());
    if (CommonClassNames.JAVA_LANG_FLOAT.equals(propertyQName) || CommonClassNames.JAVA_LANG_DOUBLE.equals(propertyQName)) {
      return new String[] {"Infinity", "-Infinity", "NaN",  "-NaN"};
    } else if (CommonClassNames.JAVA_LANG_BOOLEAN.equals(propertyQName)) {
      return new String[] {"true", "false"};
    }

    return null;
  }

  protected boolean isConstant(PsiField field) {
    return field instanceof PsiEnumConstant;
  }

  protected PsiClass getEnum() {
    final PsiClass aClass = JavaFxPsiUtil.getPropertyClass(getDeclaration());
    return aClass != null && aClass.isEnum() ? aClass : null;
  }

  @Override
  public PsiElement getEnumeratedValueDeclaration(XmlElement xmlElement, String value) {
    final PsiClass aClass = getEnum();
    if (aClass != null) {
      final PsiField fieldByName = aClass.findFieldByName(value, true);
      return fieldByName != null ? fieldByName : aClass.findFieldByName(value.toUpperCase(), true);
    }
    return xmlElement;
  }

  @Nullable
  @Override
  public String validateValue(XmlElement context, String value) {
    if (context instanceof XmlAttributeValue && value != null) {
      return validateAttributeValue((XmlAttributeValue)context, value);
    }
    return null;
  }

  @Nullable
  protected String validateAttributeValue(@NotNull XmlAttributeValue xmlAttributeValue, @NotNull String value) {
    final PsiElement parent = xmlAttributeValue.getParent();
    if (parent instanceof XmlAttribute && JavaFxPsiUtil.isEventHandlerProperty((XmlAttribute)parent)) {
      return validateAttributeHandler(xmlAttributeValue, value);
    }
    if (value.startsWith("$")) {
      return validatePropertyExpression(xmlAttributeValue, value);
    }
    else if (StringUtil.trimLeading(value).startsWith("$")) {
      return "Spaces aren't allowed before property or expression";
    }
    else {
      return validateLiteral(xmlAttributeValue, value);
    }
  }

  @Nullable
  private static String validateAttributeHandler(@NotNull XmlElement context, @NotNull String value) {
    if (value.startsWith("#")) {
      if (JavaFxPsiUtil.getControllerClass(context.getContainingFile()) == null) {
        return "No controller specified for top level element";
      }
    }
    else {
      if (JavaFxPsiUtil.parseInjectedLanguages((XmlFile)context.getContainingFile()).isEmpty()) {
        return "Page language not specified.";
      }
    }
    return null;
  }

  @Nullable
  private static String validatePropertyExpression(@NotNull XmlAttributeValue xmlAttributeValue, @NotNull String value) {
    if (JavaFxPsiUtil.isIncorrectExpressionBinding(value)) {
      return "Incorrect expression syntax";
    }
    final List<String> propertyNames = JavaFxPsiUtil.isExpressionBinding(value)
                                       ? StringUtil.split(value.substring(2, value.length() - 1), ".", true, false)
                                       : Collections.singletonList(value.substring(1));
    if (isIncompletePropertyChain(propertyNames)) {
      return "Incorrect expression syntax";
    }
    if (FxmlConstants.NULL_EXPRESSION.equals(value)) return null;

    final XmlTag currentTag = PsiTreeUtil.getParentOfType(xmlAttributeValue, XmlTag.class);
    final PsiType targetPropertyType = JavaFxPsiUtil.getWritablePropertyType(xmlAttributeValue);
    if (FxmlConstants.isNullValue(value)) {
      if (JavaFxPsiUtil.isPrimitiveOrBoxed(targetPropertyType)) {
        return "Unable to coerce to " + targetPropertyType.getPresentableText();
      }
      return null;
    }
    final PsiClass targetPropertyClass = JavaFxPsiUtil.getPropertyClass(targetPropertyType, xmlAttributeValue);
    if (targetPropertyClass == null || JavaFxPsiUtil.hasConversionFromAnyType(targetPropertyClass)) return null;

    final String firstPropertyName = propertyNames.get(0);
    final Map<String, XmlAttributeValue> fileIds = JavaFxPsiUtil.collectFileIds(currentTag);
    final PsiClass tagClass = JavaFxPsiUtil.getTagClassById(fileIds.get(firstPropertyName), firstPropertyName, xmlAttributeValue);
    if (tagClass != null) {
      PsiClass aClass = tagClass;
      final List<String> remainingPropertyNames = propertyNames.subList(1, propertyNames.size());
      for (String propertyName : remainingPropertyNames) {
        if (aClass == null) break;
        final PsiMember member = JavaFxPsiUtil.collectReadableProperties(aClass).get(propertyName);
        aClass = JavaFxPsiUtil.getPropertyClass(JavaFxPsiUtil.getReadablePropertyType(member), xmlAttributeValue);
      }
      if (aClass != null && !InheritanceUtil.isInheritorOrSelf(aClass, targetPropertyClass, true)) {
        return "Invalid value: unable to coerce to " + targetPropertyClass.getQualifiedName();
      }
    }
    return null;
  }

  public static boolean isIncompletePropertyChain(@NotNull List<String> propertyNames) {
    return propertyNames.isEmpty() || propertyNames.contains("");
  }

  @Nullable
  protected static String validateLiteral(@NotNull XmlAttributeValue xmlAttributeValue, @NotNull String value) {
    final PsiClass tagClass = JavaFxPsiUtil.getTagClass(xmlAttributeValue);
    final PsiElement declaration = JavaFxPsiUtil.getAttributeDeclaration(xmlAttributeValue);
    final String boxedQName;
    if (declaration != null) {
      boxedQName = declaration instanceof PsiMember ? JavaFxPsiUtil.getBoxedPropertyType(tagClass, (PsiMember)declaration) : null;
    }
    else {
      if (tagClass != null && !InheritanceUtil.isInheritor(tagClass, false, JavaFxCommonNames.JAVAFX_SCENE_NODE)) {
        boxedQName = tagClass.getQualifiedName();
      }
      else {
        boxedQName = null;
      }
    }
    if (boxedQName != null) {
      try {
        final Class<?> aClass = Class.forName(boxedQName);
        final Method method = aClass.getMethod(JavaFxCommonNames.VALUE_OF, String.class);
        method.invoke(aClass, value);
      }
      catch (InvocationTargetException e) {
        final Throwable cause = e.getCause();
        if (cause instanceof NumberFormatException) {
          final PsiReference reference = xmlAttributeValue.getReference();
          if (reference != null) {
            final PsiElement resolve = reference.resolve();
            if (resolve instanceof XmlAttributeValue) {
              final PsiClass resolvedClass = JavaFxPsiUtil.getTagClass((XmlAttributeValue)resolve);
              if (resolvedClass != null && boxedQName.equals(resolvedClass.getQualifiedName())) {
                return null;
              }
            }
          }
          return "Invalid value: unable to coerce to " + boxedQName;
        }
      }
      catch (Throwable ignore) {
      }
    }
    return null;
  }

  @Override
  public PsiElement getDeclaration() {
    return getDeclarationMember();
  }

  private PsiMember getDeclarationMember() {
    return JavaFxPsiUtil.collectWritableProperties(myPsiClass).get(myName);
  }

  @Override
  public PsiReference[] getValueReferences(XmlElement element, @NotNull String text) {
    return !text.startsWith("${") && !FxmlConstants.isNullValue(text) ? super.getValueReferences(element, text) : PsiReference.EMPTY_ARRAY;
  }

  @Override
  public String getName(PsiElement context) {
    return getName();
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public void init(PsiElement element) {
  }

  @Override
  public Object[] getDependences() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public String toString() {
    return (myPsiClass != null ? myPsiClass.getName() + "#" : "?#") + myName;
  }
}
