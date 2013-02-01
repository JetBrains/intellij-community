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

import com.intellij.lang.ASTNode;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.psi.xml.*;
import com.intellij.xml.XmlElementDescriptor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
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
    return findPsiClass(name, parseImports((XmlFile)tag.getContainingFile()), tag, tag.getProject());
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
    return StringUtil.isCapitalized(name) && name.equals(shortName);
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
    final String getterName = PropertyUtil.suggestGetterName(StringUtil.getShortName(attributeName), null);
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
        final XmlAttribute attribute = rootTag.getAttribute(FxmlConstants.FX_CONTROLLER);
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
      final XmlElementDescriptor descriptor = xmlTag.getDescriptor();
      if (descriptor != null) {
        final PsiElement declaration = descriptor.getDeclaration();
        if (declaration instanceof PsiClass) {
          return (PsiClass)declaration;
        }
      }
    }
    return null;
  }
}
