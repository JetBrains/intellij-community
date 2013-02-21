/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.psi.xml.*;
import com.intellij.xml.XmlElementDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * User: anna
 */
public class JavaFxPsiUtil {

  public static XmlProcessingInstruction createSingleImportInstruction(String qualifiedName, Project project) {
    final String importText = "<?import " + qualifiedName + "?>";
    final PsiElement child =
      PsiFileFactory.getInstance(project).createFileFromText("a.fxml", XMLLanguage.INSTANCE, importText).getFirstChild();
    return PsiTreeUtil.findChildOfType(child, XmlProcessingInstruction.class);
  }

  public static List<String> parseImports(XmlFile file) {
    return parseInstructions(file, "import");
  }

  public static List<String> parseInjectedLanguages(XmlFile file) {
    return parseInstructions(file, "language");
  }

  private static List<String> parseInstructions(XmlFile file, String instructionName) {
    List<String> definedImports = new ArrayList<String>();
    XmlDocument document = file.getDocument();
    if (document != null) {
      XmlProlog prolog = document.getProlog();

      final Collection<XmlProcessingInstruction>
        instructions = new ArrayList<XmlProcessingInstruction>(PsiTreeUtil.findChildrenOfType(prolog, XmlProcessingInstruction.class));
      for (final XmlProcessingInstruction instruction : instructions) {
        final String instructionTarget = getInstructionTarget(instructionName, instruction);
        if (instructionTarget != null) {
          definedImports.add(instructionTarget);
        }
      }
    }
    return definedImports;
  }

  @Nullable
  public static String getInstructionTarget(String instructionName, XmlProcessingInstruction instruction) {
    final ASTNode node = instruction.getNode();
    ASTNode xmlNameNode = node.findChildByType(XmlTokenType.XML_NAME);
    ASTNode importNode = node.findChildByType(XmlTokenType.XML_TAG_CHARACTERS);
    if (!(xmlNameNode == null || !instructionName.equals(xmlNameNode.getText()) || importNode == null)) {
      return importNode.getText();
    }
    return null;
  }

  public static PsiClass findPsiClass(String name, XmlTag tag) {
    final Project project = tag.getProject();
    if (!StringUtil.getShortName(name).equals(name)) {
      return JavaPsiFacade.getInstance(project).findClass(name, GlobalSearchScope.allScope(project));
    }
    return findPsiClass(name, parseImports((XmlFile)tag.getContainingFile()), tag, project);
  }

  private static PsiClass findPsiClass(String name, List<String> imports, XmlTag tag, Project project) {
    PsiClass psiClass = null;
    if (imports != null) {
      JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);

      PsiFile file = tag.getContainingFile();
      for (String anImport : imports) {
        if (StringUtil.endsWith(anImport, "." + name)) {
          psiClass = psiFacade.findClass(anImport, file.getResolveScope()); 
        } else if (StringUtil.endsWith(anImport, ".*")) {
          psiClass = psiFacade.findClass(StringUtil.trimEnd(anImport, "*") + name, file.getResolveScope());
        }
        if (psiClass != null) {
          return psiClass;
        }
      }
    }
    return psiClass;
  }

  public static void insertImportWhenNeeded(XmlFile xmlFile,
                                            String shortName,
                                            String qualifiedName) {
    if (shortName != null && findPsiClass(shortName, xmlFile.getRootTag()) == null) {
      final XmlDocument document = xmlFile.getDocument();
      if (document != null) {
        final XmlProcessingInstruction processingInstruction = createSingleImportInstruction(qualifiedName, xmlFile.getProject());
        final XmlProlog prolog = document.getProlog();
        if (prolog != null) {
          prolog.add(processingInstruction);
        } else {
          document.addBefore(processingInstruction, document.getRootTag());
        }
        PostprocessReformattingAspect.getInstance(xmlFile.getProject()).doPostponedFormatting(xmlFile.getViewProvider());
      }
    }
  }

  public static PsiClass getPropertyClass(PsiElement field) {
    final PsiClassType classType = getPropertyClassType(field);
    return classType != null ? classType.resolve() : null;
  }

  public static PsiClassType getPropertyClassType(PsiElement field) {
    if (field instanceof PsiField) {
      final PsiType type = ((PsiField)field).getType();
      if (type instanceof PsiClassType) {
        final PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)type).resolveGenerics();
        final PsiClass attributeClass = resolveResult.getElement();
        if (attributeClass != null) {
          final PsiClass objectProperty = JavaPsiFacade.getInstance(attributeClass.getProject())
            .findClass(JavaFxCommonClassNames.JAVAFX_BEANS_PROPERTY_OBJECT_PROPERTY, attributeClass.getResolveScope());
          if (objectProperty != null) {
            final PsiSubstitutor superClassSubstitutor = TypeConversionUtil
              .getClassSubstitutor(objectProperty, attributeClass, resolveResult.getSubstitutor());
            if (superClassSubstitutor != null) {
              final PsiType propertyType = superClassSubstitutor.substitute(objectProperty.getTypeParameters()[0]);
              if (propertyType instanceof PsiClassType) {
                return (PsiClassType)propertyType;
              }
            }
          }
        }
      }
    }
    return null;
  }

  public static boolean isClassTag(String name) {
    final String shortName = StringUtil.getShortName(name);
    final boolean capitalized = StringUtil.isCapitalized(name);
    if (name.equals(shortName)) {
      return capitalized;
    }
    return !capitalized;
  }

  public static PsiMethod findPropertySetter(String attributeName, XmlTag context) {
    final String packageName = StringUtil.getPackageName(attributeName);
    if (context != null && !StringUtil.isEmptyOrSpaces(packageName)) {
      final PsiClass classWithStaticProperty = findPsiClass(packageName, context);
      if (classWithStaticProperty != null) {
        return findPropertySetter(attributeName, classWithStaticProperty);
      }
    }
    return null;
  }

  public static PsiMethod findPropertySetter(String attributeName, PsiClass classWithStaticProperty) {
    final String setterName = PropertyUtil.suggestSetterName(StringUtil.getShortName(attributeName));
    final PsiMethod[] setters = classWithStaticProperty.findMethodsByName(setterName, true);
    if (setters.length == 1) {
      return setters[0];
    }
    return null;
  }

  public static PsiMethod findPropertyGetter(String attributeName, PsiClass classWithStaticProperty) {
    PsiMethod getter = findPropertyGetter(attributeName, classWithStaticProperty, null);
    if (getter != null) {
      return getter;
    }
    return findPropertyGetter(attributeName, classWithStaticProperty, PsiType.BOOLEAN);
  }

  private static PsiMethod findPropertyGetter(final String attributeName,
                                              final PsiClass classWithStaticProperty,
                                              final PsiType propertyType) {
    final String getterName = PropertyUtil.suggestGetterName(StringUtil.getShortName(attributeName), propertyType);
    final PsiMethod[] getters = classWithStaticProperty.findMethodsByName(getterName, true);
    if (getters.length >= 1) {
      return getters[0];
    }
    return null;
  }
  
  public static PsiClass getControllerClass(PsiFile containingFile) {
    if (containingFile instanceof XmlFile) {
      final XmlTag rootTag = ((XmlFile)containingFile).getRootTag();
      if (rootTag != null) {
        XmlAttribute attribute = rootTag.getAttribute(FxmlConstants.FX_CONTROLLER);
        if (attribute == null && FxmlConstants.FX_ROOT.equals(rootTag.getName())) {
          attribute = rootTag.getAttribute(FxmlConstants.TYPE);
        }
        if (attribute != null) {
          final String attributeValue = attribute.getValue();
          if (!StringUtil.isEmptyOrSpaces(attributeValue)) {
            return  JavaPsiFacade.getInstance(containingFile.getProject()).findClass(attributeValue, containingFile.getResolveScope());
          }
        }
      }
    }
    return null;
  }

  public static boolean checkIfAttributeHandler(XmlAttribute attribute) {
    final String attributeName = attribute.getName();
    final XmlTag xmlTag = attribute.getParent();
    final XmlElementDescriptor descriptor = xmlTag.getDescriptor();
    if (descriptor == null) return false;
    final PsiElement currentTagClass = descriptor.getDeclaration();
    if (!(currentTagClass instanceof PsiClass)) return false;
    final PsiField handlerField = ((PsiClass)currentTagClass).findFieldByName(attributeName, true);
    if (handlerField == null) {
      return false;
    }
    final PsiClass objectPropertyClass = getPropertyClass(handlerField);
    if (objectPropertyClass == null || !InheritanceUtil.isInheritor(objectPropertyClass, JavaFxCommonClassNames.JAVAFX_EVENT_EVENT_HANDLER)) {
      return false;
    }
    return true;
  }

  @Nullable
  public static PsiClass getTagClass(XmlAttributeValue xmlAttributeValue) {
    if (xmlAttributeValue == null) return null;
    final PsiElement xmlAttribute = xmlAttributeValue.getParent();
    final XmlTag xmlTag = ((XmlAttribute)xmlAttribute).getParent();
    if (xmlTag != null) {
      return getTagClass(xmlTag);
    }
    return null;
  }

  public static PsiClass getTagClass(XmlTag xmlTag) {
    final XmlElementDescriptor descriptor = xmlTag.getDescriptor();
    if (descriptor != null) {
      final PsiElement declaration = descriptor.getDeclaration();
      if (declaration instanceof PsiClass) {
        return (PsiClass)declaration;
      }
    }
    return null;
  }

  public static boolean isVisibleInFxml(PsiMember psiMember) {
    return psiMember.hasModifierProperty(PsiModifier.PUBLIC) || 
           AnnotationUtil.isAnnotated(psiMember, JavaFxCommonClassNames.JAVAFX_FXML_ANNOTATION, false);
  }

  public static PsiMethod findValueOfMethod(@NotNull final PsiClass tagClass) {
    final PsiMethod[] methods = tagClass.findMethodsByName(JavaFxCommonClassNames.VALUE_OF, false);
    for (PsiMethod method : methods) {
      if (method.hasModifierProperty(PsiModifier.STATIC)) {
        final PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length == 1) {
          final PsiType type = parameters[0].getType();
          if (type.equalsToText(CommonClassNames.JAVA_LANG_STRING) || type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
            if (method.hasModifierProperty(PsiModifier.STATIC) && tagClass.equals(PsiUtil.resolveClassInType(method.getReturnType()))) {
              return method;
            }
          }
        }
      }
    }
    return null;
  }
  
  public static boolean isReadOnly(String attributeName, XmlTag tag) {
    final XmlElementDescriptor descriptor = tag.getDescriptor();
    if (descriptor != null) {
      final PsiElement declaration = descriptor.getDeclaration();
      if (declaration instanceof PsiClass) {
        final PsiClass psiClass = (PsiClass)declaration;
        final PsiField psiField = psiClass.findFieldByName(attributeName, true);
        if (psiField != null) {
          if (findPropertySetter(attributeName, (PsiClass)declaration) == null &&
              findPropertySetter(attributeName, tag) == null && 
              !InheritanceUtil.isInheritor(psiField.getType(), "javafx.collections.ObservableList")) {
            //todo read only condition?
            final PsiMethod[] constructors = psiClass.getConstructors();
            for (PsiMethod constructor : constructors) {
              for (PsiParameter parameter : constructor.getParameterList().getParameters()) {
                if (psiField.getType().equals(parameter.getType())) {
                  return false;
                }
              }
            }
            return true;
          }
        }
      }
    }
    return false;
    
  }

  public static boolean isExpressionBinding(String value) {
    if (!value.startsWith("$")) return false;
    value = value.substring(1);
    return value.startsWith("{") && value.endsWith("}") && value.contains(".");
  }

  @Nullable
  public static PsiType getPropertyType(final PsiType type, final Project project) {
    final PsiClass psiClass = PsiUtil.resolveClassInType(type);
    if (psiClass != null) {
      final PsiClass propertyClass = JavaPsiFacade.getInstance(project).findClass(JavaFxCommonClassNames.JAVAFX_BEANS_PROPERTY,
                                                                                  GlobalSearchScope.allScope(project));
      if (propertyClass != null) {
        final PsiSubstitutor substitutor =
          TypeConversionUtil.getClassSubstitutor(propertyClass, psiClass, PsiSubstitutor.EMPTY);
        if (substitutor != null) {
          return substitutor.substitute(propertyClass.getTypeParameters()[0]);
        }
      }
    }
    return null;
  }

  public static PsiType getDefaultPropertyExpectedType(PsiClass aClass) {
    final PsiAnnotation annotation = AnnotationUtil.findAnnotationInHierarchy(aClass, Collections.singleton(JavaFxCommonClassNames.JAVAFX_BEANS_DEFAULT_PROPERTY));
    if (annotation != null) {
      final PsiAnnotationMemberValue memberValue = annotation.findAttributeValue(null);
      if (memberValue != null) {
        final String propertyName = StringUtil.stripQuotesAroundValue(memberValue.getText());
        final PsiMethod getter = findPropertyGetter(propertyName, aClass);
        if (getter != null) {
          return getter.getReturnType();
        }
      }
    }
    return null;
  }
  
  public static String getDefaultPropertyName(PsiClass aClass) {
    final PsiAnnotation annotation = AnnotationUtil.findAnnotationInHierarchy(aClass, 
                                                                              Collections.singleton(JavaFxCommonClassNames.JAVAFX_BEANS_DEFAULT_PROPERTY));
    if (annotation != null) {
      final PsiAnnotationMemberValue memberValue = annotation.findAttributeValue(null);
      if (memberValue != null) {
        return StringUtil.stripQuotesAroundValue(memberValue.getText());
      }
    }
    return null;
  }
}
