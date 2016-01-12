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
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.*;
import com.intellij.psi.xml.*;
import com.intellij.util.Processor;
import com.intellij.xml.XmlElementDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxClassBackedElementDescriptor;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxDefaultPropertyElementDescriptor;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxPropertyElementDescriptor;

import java.util.*;

/**
 * User: anna
 */
public class JavaFxPsiUtil {

  private static final Logger LOG = Logger.getInstance("#" + JavaFxPsiUtil.class.getName());

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

  public static PsiClass findPsiClass(String name, PsiElement context) {
    final Project project = context.getProject();
    if (!StringUtil.getShortName(name).equals(name)) {
      return JavaPsiFacade.getInstance(project).findClass(name, GlobalSearchScope.allScope(project));
    }
    return findPsiClass(name, parseImports((XmlFile)context.getContainingFile()), context, project);
  }

  private static PsiClass findPsiClass(String name, List<String> imports, PsiElement context, Project project) {
    PsiClass psiClass = null;
    if (imports != null) {
      JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);

      PsiFile file = context.getContainingFile();
      for (String anImport : imports) {
        if (StringUtil.getShortName(anImport).equals(name)) {
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
    return getPropertyClassType(field, JavaFxCommonClassNames.JAVAFX_BEANS_PROPERTY_OBJECT_PROPERTY);
  }

  public static PsiClassType getPropertyClassType(PsiElement field, final String superTypeFQN) {
    if (field instanceof PsiMember) {
      final PsiType type = PropertyUtil.getPropertyType((PsiMember)field);
      if (type instanceof PsiClassType) {
        final PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)type).resolveGenerics();
        final PsiClass attributeClass = resolveResult.getElement();
        if (attributeClass != null) {
          final PsiClass objectProperty = JavaPsiFacade.getInstance(attributeClass.getProject())
            .findClass(superTypeFQN, attributeClass.getResolveScope());
          if (objectProperty != null) {
            final PsiSubstitutor superClassSubstitutor = TypeConversionUtil
              .getClassSubstitutor(objectProperty, attributeClass, resolveResult.getSubstitutor());
            if (superClassSubstitutor != null) {
              final PsiType propertyType = superClassSubstitutor.substitute(objectProperty.getTypeParameters()[0]);
              if (propertyType instanceof PsiClassType) {
                return (PsiClassType)propertyType;
              }
            }
            else {
              return (PsiClassType)type;
            }
          }
        }
      }
    }
    return null;
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
    if (setters.length >= 1) {
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

  private static final Key<CachedValue<PsiClass>> INJECTED_CONTROLLER = Key.create("javafx.injected.controller");
  private static final RecursionGuard ourGuard = RecursionManager.createGuard("javafx.controller");
  public static PsiClass getControllerClass(final PsiFile containingFile) {
    if (containingFile instanceof XmlFile) {
      final XmlTag rootTag = ((XmlFile)containingFile).getRootTag();
      final Project project = containingFile.getProject();
      if (rootTag != null) {
        XmlAttribute attribute = rootTag.getAttribute(FxmlConstants.FX_CONTROLLER);
        if (attribute != null) {
          final PsiClass controllerClass = findControllerClass(containingFile, project, attribute);
          if (controllerClass != null) {
            return controllerClass;
          }
        }
      }
      final CachedValuesManager manager = CachedValuesManager.getManager(containingFile.getProject());
      final PsiClass injectedControllerClass = ourGuard.doPreventingRecursion(containingFile, true, new Computable<PsiClass>() {
        @Override
        public PsiClass compute() {
          return manager.getCachedValue(containingFile, INJECTED_CONTROLLER,
                                        new JavaFxControllerCachedValueProvider(containingFile.getProject(), containingFile), true);
        }
      });
      if (injectedControllerClass != null) {
        return injectedControllerClass;
      }

      if (rootTag != null && FxmlConstants.FX_ROOT.equals(rootTag.getName())) {
        final XmlAttribute rootTypeAttr = rootTag.getAttribute(FxmlConstants.TYPE);
        if (rootTypeAttr != null) {
          return findControllerClass(containingFile, project, rootTypeAttr);
        }
      }
    }
    return null;
  }

  private static PsiClass findControllerClass(PsiFile containingFile, Project project, XmlAttribute attribute) {
    final String attributeValue = attribute.getValue();
    if (!StringUtil.isEmptyOrSpaces(attributeValue)) {
      final GlobalSearchScope customScope = GlobalSearchScope.projectScope(project).intersectWith(containingFile.getResolveScope());
      return  JavaPsiFacade.getInstance(project).findClass(attributeValue, customScope);
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
      final String suggestedSetterName = PropertyUtil.suggestSetterName(attributeName);
      final PsiMethod[] existingSetters = ((PsiClass)currentTagClass).findMethodsByName(suggestedSetterName, true);
      for (PsiMethod setter : existingSetters) {
        final PsiParameter[] parameters = setter.getParameterList().getParameters();
        if (parameters.length == 1 && InheritanceUtil.isInheritor(parameters[0].getType(), JavaFxCommonClassNames.JAVAFX_EVENT_EVENT_HANDLER)) {
          return true;
        }
      }
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
          return isReadOnly(psiClass, psiField);
        }
      }
    }
    return false;

  }

  public static boolean isReadOnly(PsiClass psiClass, PsiField psiField) {
    final String name = psiField.getName();
    if (findPropertySetter(name, psiClass) == null &&
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
    return false;
  }

  public static boolean isExpressionBinding(String value) {
    if (!value.startsWith("$")) return false;
    value = value.substring(1);
    return value.startsWith("{") && value.endsWith("}") && value.contains(".");
  }

  @Nullable
  public static PsiType getPropertyType(final PsiType type, final Project project) {
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(type);
    final PsiClass psiClass = resolveResult.getElement();
    if (psiClass != null) {
      final PsiClass propertyClass = JavaPsiFacade.getInstance(project).findClass(JavaFxCommonClassNames.JAVAFX_BEANS_PROPERTY,
                                                                                  GlobalSearchScope.allScope(project));
      if (propertyClass != null) {
        final PsiSubstitutor substitutor =
          TypeConversionUtil.getClassSubstitutor(propertyClass, psiClass, resolveResult.getSubstitutor());
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

  public static String getDefaultPropertyName(@Nullable PsiClass aClass) {
    if (aClass == null) {
      return null;
    }
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

  public static String isAbleToInstantiate(final PsiClass psiClass) {
    if(psiClass.getConstructors().length > 0) {
      for (PsiMethod constr : psiClass.getConstructors()) {
        final PsiParameter[] parameters = constr.getParameterList().getParameters();
        if (parameters.length == 0) return null;
        boolean annotated = true;
        for (PsiParameter parameter : parameters) {
          if (!AnnotationUtil.isAnnotated(parameter, JavaFxCommonClassNames.JAVAFX_BEANS_NAMED_ARG, false)) {
            annotated = false;
            break;
          }
        }
        if (annotated) return null;
      }
      final PsiMethod valueOf = findValueOfMethod(psiClass);
      if (valueOf == null) {
        if (!hasBuilder(psiClass)) return "Unable to instantiate";
      }
    }
    return null;
  }

  public static boolean hasBuilder(@NotNull final PsiClass psiClass) {
    final Project project = psiClass.getProject();
    return CachedValuesManager.getManager(project).getCachedValue(psiClass, new CachedValueProvider<Boolean>() {
      @Nullable
      @Override
      public Result<Boolean> compute() {
        final PsiClass builderClass = JavaPsiFacade.getInstance(project).findClass(JavaFxCommonClassNames.JAVAFX_FXML_BUILDER,
                                                                                   GlobalSearchScope.allScope(project));
        if (builderClass != null) {
          final PsiMethod[] buildMethods = builderClass.findMethodsByName("build", false);
          if (buildMethods.length == 1 && buildMethods[0].getParameterList().getParametersCount() == 0) {
            if (ClassInheritorsSearch.search(builderClass).forEach(new Processor<PsiClass>() {
              @Override
              public boolean process(PsiClass aClass) {
                PsiType returnType = null;
                final PsiMethod method = MethodSignatureUtil.findMethodBySuperMethod(aClass, buildMethods[0], false);
                if (method != null) {
                  returnType = method.getReturnType();
                }
                return !Comparing.equal(psiClass, PsiUtil.resolveClassInClassTypeOnly(returnType));
              }
            })) {
              return Result.create(false, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
            }
          }
        }
        return Result.create(true, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
      }
    });
  }

  public static String isClassAcceptable(@Nullable XmlTag parentTag, @Nullable final PsiClass aClass) {
    if (parentTag == null) {
      return null;
    }
    if (aClass != null && aClass.isValid()) {
      XmlElementDescriptor descriptor = parentTag.getDescriptor();
      if (descriptor instanceof JavaFxDefaultPropertyElementDescriptor) {
        descriptor = ((JavaFxDefaultPropertyElementDescriptor)descriptor).getFxRootTagDescriptor(parentTag);
      }

      if (descriptor instanceof JavaFxPropertyElementDescriptor) {
        final PsiElement declaration = descriptor.getDeclaration();
        if (declaration instanceof PsiField) {
          return canCoerce(aClass, ((PsiField)declaration).getType());
        }
      }
      else if (descriptor instanceof JavaFxClassBackedElementDescriptor) {
        final PsiElement declaration = descriptor.getDeclaration();
        if (declaration instanceof PsiClass) {
          final PsiType type = getDefaultPropertyExpectedType((PsiClass)declaration);
          if (type != null) {
            return canCoerce(aClass, type);
          }
        }
      }
    }
    return null;
  }

  private static String canCoerce(PsiClass aClass, PsiType type) {
    PsiType collectionItemType = JavaGenericsUtil.getCollectionItemType(type, aClass.getResolveScope());
    if (collectionItemType == null && InheritanceUtil.isInheritor(type, JavaFxCommonClassNames.JAVAFX_BEANS_PROPERTY)) {
      collectionItemType = getPropertyType(type, aClass.getProject());
    }
    if (collectionItemType != null && PsiPrimitiveType.getUnboxedType(collectionItemType) == null) {
      final PsiClass baseClass = PsiUtil.resolveClassInType(collectionItemType);
      if (baseClass != null) {
        final String qualifiedName = baseClass.getQualifiedName();
        if (qualifiedName != null && !Comparing.strEqual(qualifiedName, CommonClassNames.JAVA_LANG_STRING)) {
          if (!InheritanceUtil.isInheritor(aClass, qualifiedName)) {
             return unableToCoerceMessage(aClass, qualifiedName);
          }
        }
      }
    }
    return null;
  }

  private static String unableToCoerceMessage(PsiClass aClass, String qualifiedName) {
    return "Unable to coerce " + HighlightUtil.formatClass(aClass)+ " to " + qualifiedName;
  }

  public static boolean isOutOfHierarchy(final XmlAttributeValue element) {
    XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
    while (tag != null) {
      if (FxmlConstants.FX_DEFINE.equals(tag.getName())) {
        return true;
      }
      tag = tag.getParentTag();
    }
    return false;
  }

  public static PsiType getWrappedPropertyType(final PsiField field, final Project project, final Map<String, PsiType> typeMap) {
    return CachedValuesManager.getCachedValue(field, new CachedValueProvider<PsiType>() {
      @Nullable
      @Override
      public Result<PsiType> compute() {
        final PsiType fieldType = field.getType();
        final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(fieldType);
        final PsiClass fieldClass = resolveResult.getElement();
        if (fieldClass == null) return Result.create(fieldType, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
        PsiType substitute = null;
        for (String typeName : typeMap.keySet()) {
          if (InheritanceUtil.isInheritor(fieldType, typeName)) {
            substitute = typeMap.get(typeName);
            break;
          }
        }
        if (substitute == null) {
          if (!InheritanceUtil.isInheritor(fieldType, JavaFxCommonClassNames.JAVAFX_BEANS_VALUE_OBSERVABLE_VALUE)) {
            return Result.create(fieldType, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
          }
          final PsiClass aClass = JavaPsiFacade.getInstance(project)
            .findClass(JavaFxCommonClassNames.JAVAFX_BEANS_VALUE_OBSERVABLE_VALUE, GlobalSearchScope.allScope(project));
          LOG.assertTrue(aClass != null);
          final PsiSubstitutor substitutor =
            TypeConversionUtil.getSuperClassSubstitutor(aClass, fieldClass, resolveResult.getSubstitutor());
          final PsiMethod[] values = aClass.findMethodsByName("getValue", false);
          substitute = substitutor.substitute(values[0].getReturnType());
        }

        return Result.create(substitute, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
      }
    });
  }

  private static class JavaFxControllerCachedValueProvider implements CachedValueProvider<PsiClass> {
    private final Project myProject;
    private final PsiFile myContainingFile;

    public JavaFxControllerCachedValueProvider(Project project, PsiFile containingFile) {
      myProject = project;
      myContainingFile = containingFile;
    }

    @Nullable
    @Override
    public Result<PsiClass> compute() {
      final Ref<PsiClass> injectedController = new Ref<PsiClass>();
      final Ref<PsiFile> dep = new Ref<PsiFile>();
      final PsiClass fxmlLoader =
        JavaPsiFacade.getInstance(myProject).findClass(JavaFxCommonClassNames.JAVAFX_FXML_FXMLLOADER, GlobalSearchScope.allScope(myProject));
      if (fxmlLoader != null) {
        final PsiMethod[] injectControllerMethods = fxmlLoader.findMethodsByName("setController", false);
        if (injectControllerMethods.length == 1) {
          final JavaFxRetrieveControllerProcessor processor = new JavaFxRetrieveControllerProcessor() {
            @Override
            protected boolean isResolveToSetter(PsiMethodCallExpression methodCallExpression) {
              return methodCallExpression.resolveMethod() == injectControllerMethods[0];
            }
          };
          final GlobalSearchScope globalSearchScope = GlobalSearchScope
                      .notScope(GlobalSearchScope.getScopeRestrictedByFileTypes(myContainingFile.getResolveScope(), StdFileTypes.XML));
          ReferencesSearch.search(myContainingFile, globalSearchScope).forEach(new Processor<PsiReference>() {
            @Override
            public boolean process(PsiReference reference) {
              final PsiElement element = reference.getElement();
              if (element instanceof PsiLiteralExpression) {
                final PsiNewExpression expression = PsiTreeUtil.getParentOfType(element, PsiNewExpression.class);
                if (expression != null) {
                  final PsiType type = expression.getType();
                  if (type != null && type.equalsToText(JavaFxCommonClassNames.JAVAFX_FXML_FXMLLOADER)) {
                    final PsiElement parent = expression.getParent();
                    if (parent instanceof PsiLocalVariable) {
                      ReferencesSearch.search(parent).forEach(processor);
                      final PsiClass controller = processor.getInjectedController();
                      if (controller != null) {
                        injectedController.set(controller);
                        dep.set(processor.getContainingFile());
                        return false;
                      }
                    }
                  }
                }
              }
              return true;
            }
          });
        }
      }
      return new Result<PsiClass>(injectedController.get(), dep.get() != null ? dep.get() : PsiModificationTracker.MODIFICATION_COUNT);
    }

    private static abstract class JavaFxRetrieveControllerProcessor implements Processor<PsiReference> {
      private final Ref<PsiClass> myInjectedController = new Ref<PsiClass>();
      private final Ref<PsiFile> myContainingFile = new Ref<PsiFile>();

      protected abstract boolean isResolveToSetter(PsiMethodCallExpression methodCallExpression);

      @Override
      public boolean process(PsiReference reference) {
        final PsiElement element = reference.getElement();
        if (element instanceof PsiReferenceExpression) {
          final PsiMethodCallExpression methodCallExpression = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
          if (methodCallExpression != null && isResolveToSetter(methodCallExpression)) {
            final PsiExpression[] expressions = methodCallExpression.getArgumentList().getExpressions();
            if (expressions.length > 0) {
              final PsiClass psiClass = PsiUtil.resolveClassInType(expressions[0].getType());
              if (psiClass != null) {
                myInjectedController.set(psiClass);
                myContainingFile.set(methodCallExpression.getContainingFile());
                return false;
              }
            }
          }
        }
        return true;
      }

      private PsiClass getInjectedController() {
        return myInjectedController.get();
      }

      private PsiFile getContainingFile() {
        return myContainingFile.get();
      }
    }
  }
}
