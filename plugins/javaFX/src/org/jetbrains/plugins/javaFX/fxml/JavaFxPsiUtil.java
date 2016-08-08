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
import com.intellij.codeInsight.daemon.Validator;
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
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxClassTagDescriptorBase;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxPropertyTagDescriptor;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
    final List<String> definedImports = new ArrayList<>();
    final XmlDocument document = file.getDocument();
    if (document != null) {
      final XmlProlog prolog = document.getProlog();

      final Collection<XmlProcessingInstruction>
        instructions = new ArrayList<>(PsiTreeUtil.findChildrenOfType(prolog, XmlProcessingInstruction.class));
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
      final PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(name, GlobalSearchScope.allScope(project));
      if (psiClass != null) {
        return psiClass;
      }
      return findNestedPsiClass(name, context, project);
    }
    return findPsiClass(name, parseImports((XmlFile)context.getContainingFile()), context, project);
  }

  private static PsiClass findNestedPsiClass(String name, PsiElement context, Project project) {
    final int dotIndex = name.indexOf('.');
    if (dotIndex > 0) {
      final String outerName = name.substring(0, dotIndex);
      final PsiClass outerClass = findPsiClass(outerName, parseImports((XmlFile)context.getContainingFile()), context, project);
      if (outerClass != null) {
        final List<String> nameChain = StringUtil.split(name, ".", true, false);
        final List<String> nestedNames = nameChain.subList(1, nameChain.size());
        PsiClass aClass = outerClass;
        for (String nestedName : nestedNames) {
          aClass = aClass.findInnerClassByName(nestedName, true);
          if (aClass == null) return null;
        }
        return aClass;
      }
    }
    return null;
  }

  private static PsiClass findPsiClass(String name, List<String> imports, PsiElement context, Project project) {
    PsiClass psiClass = null;
    if (imports != null) {
      JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);

      PsiFile file = context.getContainingFile();
      for (String anImport : imports) {
        if (StringUtil.getShortName(anImport).equals(name)) {
          psiClass = psiFacade.findClass(anImport, file.getResolveScope());
        }
        else if (StringUtil.endsWith(anImport, ".*")) {
          psiClass = psiFacade.findClass(StringUtil.trimEnd(anImport, "*") + name, file.getResolveScope());
        }
        if (psiClass != null) {
          return psiClass;
        }
      }
    }
    return null;
  }

  public static void insertImportWhenNeeded(XmlFile xmlFile,
                                            String shortName,
                                            String qualifiedName) {
    if (shortName != null && qualifiedName != null && findPsiClass(shortName, xmlFile.getRootTag()) == null) {
      final XmlDocument document = xmlFile.getDocument();
      if (document != null) {
        final XmlProcessingInstruction processingInstruction = createSingleImportInstruction(qualifiedName, xmlFile.getProject());
        final XmlProlog prolog = document.getProlog();
        if (prolog != null) {
          prolog.add(processingInstruction);
        }
        else {
          document.addBefore(processingInstruction, document.getRootTag());
        }
        PostprocessReformattingAspect.getInstance(xmlFile.getProject()).doPostponedFormatting(xmlFile.getViewProvider());
      }
    }
  }

  public static PsiClass getPropertyClass(PsiElement member) {
    final PsiClassType classType = getPropertyClassType(member);
    return classType != null ? classType.resolve() : null;
  }

  public static PsiClassType getPropertyClassType(PsiElement member) {
    return getPropertyClassType(member, JavaFxCommonNames.JAVAFX_BEANS_PROPERTY_OBJECT_PROPERTY);
  }

  public static PsiClassType getPropertyClassType(PsiElement member, final String superTypeFQN) {
    if (member instanceof PsiMember) {
      final PsiType type = PropertyUtil.getPropertyType((PsiMember)member);
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

  public static PsiMethod findStaticPropertySetter(String attributeName, XmlTag context) {
    final String packageName = StringUtil.getPackageName(attributeName);
    if (context != null && !StringUtil.isEmptyOrSpaces(packageName)) {
      final PsiClass classWithStaticProperty = findPsiClass(packageName, context);
      if (classWithStaticProperty != null) {
        return findStaticPropertySetter(attributeName, classWithStaticProperty);
      }
    }
    return null;
  }

  @Nullable
  public static PsiMethod findStaticPropertySetter(@NotNull String attributeName, @Nullable PsiClass classWithStaticProperty) {
    if (classWithStaticProperty == null) return null;
    final String setterName = PropertyUtil.suggestSetterName(StringUtil.getShortName(attributeName));
    final PsiMethod[] setters = classWithStaticProperty.findMethodsByName(setterName, true);
    for (PsiMethod setter : setters) {
      if (setter.hasModifierProperty(PsiModifier.PUBLIC) &&
          setter.hasModifierProperty(PsiModifier.STATIC) &&
          setter.getParameterList().getParametersCount() == 2) {
        return setter;
      }
    }
    return null;
  }

  public static PsiMethod findPropertyGetter(@NotNull PsiClass psiClass, @Nullable String propertyName) {
    if (StringUtil.isEmpty(propertyName)) return null;
    PsiMethod getter = findPropertyGetter(psiClass, propertyName, null);
    if (getter != null) {
      return getter;
    }
    return findPropertyGetter(psiClass, propertyName, PsiType.BOOLEAN);
  }

  private static PsiMethod findPropertyGetter(final PsiClass psiClass, final String propertyName, final PsiType propertyType) {
    final String getterName = PropertyUtil.suggestGetterName(propertyName, propertyType);
    final PsiMethod[] getters = psiClass.findMethodsByName(getterName, true);
    for (PsiMethod getter : getters) {
      if (getter.hasModifierProperty(PsiModifier.PUBLIC) &&
          !getter.hasModifierProperty(PsiModifier.STATIC) &&
          PropertyUtil.isSimplePropertyGetter(getter)) {
        return getter;
      }
    }
    return null;
  }

  public static PsiMethod findObservablePropertyGetter(@NotNull PsiClass psiClass, @Nullable String propertyName) {
    if (StringUtil.isEmpty(propertyName)) return null;
    final PsiMethod[] getters = psiClass.findMethodsByName(propertyName + JavaFxCommonNames.PROPERTY_METHOD_SUFFIX, true);
    for (PsiMethod getter : getters) {
      if (getter.hasModifierProperty(PsiModifier.PUBLIC) &&
          !getter.hasModifierProperty(PsiModifier.STATIC) &&
          getter.getParameterList().getParametersCount() == 0 &&
          InheritanceUtil.isInheritor(getter.getReturnType(), JavaFxCommonNames.JAVAFX_BEANS_VALUE_OBSERVABLE_VALUE)) {
        return getter;
      }
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
      final PsiClass injectedControllerClass = ourGuard.doPreventingRecursion(containingFile, true,
                                                                              () -> manager.getCachedValue(containingFile, INJECTED_CONTROLLER,
                                                                                                                                                                               new JavaFxControllerCachedValueProvider(containingFile.getProject(), containingFile), true));
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
      return JavaPsiFacade.getInstance(project).findClass(attributeValue, customScope);
    }
    return null;
  }

  public static boolean isEventHandlerProperty(@NotNull XmlAttribute attribute) {
    final PsiClass tagClass = getTagClass(attribute.getParent());
    return tagClass != null && getEventHandlerPropertyType(tagClass, attribute.getName()) != null;
  }

  @Nullable
  public static PsiClass getTagClass(@Nullable XmlAttributeValue xmlAttributeValue) {
    if (xmlAttributeValue != null) {
      final PsiElement parent = xmlAttributeValue.getParent();
      if (parent instanceof XmlAttribute) {
        final XmlTag xmlTag = ((XmlAttribute)parent).getParent();
        return getTagClass(xmlTag);
      }
    }
    return null;
  }

  @Nullable
  public static PsiClass getTagClass(@Nullable XmlTag xmlTag) {
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

  @Nullable
  public static PsiElement getAttributeDeclaration(@Nullable XmlAttributeValue xmlAttributeValue) {
    if (xmlAttributeValue != null) {
      final PsiElement parent = xmlAttributeValue.getParent();
      if (parent instanceof XmlAttribute) {
        final XmlAttributeDescriptor descriptor = ((XmlAttribute)parent).getDescriptor();
        if (descriptor != null) {
          return descriptor.getDeclaration();
        }
      }
    }
    return null;
  }

  public static boolean isVisibleInFxml(@NotNull PsiMember psiMember) {
    return psiMember.hasModifierProperty(PsiModifier.PUBLIC) ||
           AnnotationUtil.isAnnotated(psiMember, JavaFxCommonNames.JAVAFX_FXML_ANNOTATION, false);
  }

  @Nullable
  public static PsiMethod findValueOfMethod(@NotNull final PsiType psiType) {
    final PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(psiType);
    return psiClass != null ? findValueOfMethod(psiClass) : null;
  }

  @Nullable
  public static PsiMethod findValueOfMethod(@NotNull final PsiClass psiClass) {
    return CachedValuesManager.getCachedValue(psiClass, () -> {
      final PsiMethod[] methods = psiClass.findMethodsByName(JavaFxCommonNames.VALUE_OF, true);
      for (PsiMethod method : methods) {
        if (method.hasModifierProperty(PsiModifier.STATIC)) {
          final PsiParameter[] parameters = method.getParameterList().getParameters();
          if (parameters.length == 1) {
            final PsiType type = parameters[0].getType();
            if (type.equalsToText(CommonClassNames.JAVA_LANG_STRING) || type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
              if (psiClass.equals(PsiUtil.resolveClassInType(method.getReturnType()))) {
                return CachedValueProvider.Result.create(method, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
              }
            }
          }
        }
      }
      return CachedValueProvider.Result.create(null, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
    });
  }

  public static boolean isReadOnly(String attributeName, XmlTag tag) {
    if (findStaticPropertySetter(attributeName, tag) != null) return false;
    final XmlElementDescriptor descriptor = tag.getDescriptor();
    if (descriptor instanceof JavaFxClassTagDescriptorBase) {
      return ((JavaFxClassTagDescriptorBase)descriptor).isReadOnlyAttribute(attributeName);
    }
    return false;
  }

  public static boolean isExpressionBinding(@Nullable String value) {
    return value != null && value.startsWith("${") && value.endsWith("}");
  }

  public static boolean isIncorrectExpressionBinding(@Nullable String value) {
    if (value == null || !value.startsWith("$")) return false;
    if (value.length() == 1) return true;
    final boolean expressionStarts = value.startsWith("${");
    final boolean expressionEnds = value.endsWith("}");
    if (expressionStarts && expressionEnds && value.length() == 3) return true;
    if (expressionStarts != expressionEnds) return true;
    if (expressionStarts && value.indexOf('{', 2) >= 2) return true;
    if (expressionEnds && value.indexOf('}') < value.length() - 1) return true;
    return false;
  }

  @Nullable
  public static PsiType getWritablePropertyType(@Nullable final PsiType type, @NotNull final Project project) {
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(type);
    final PsiClass psiClass = resolveResult.getElement();
    if (psiClass != null) {
      final PsiClass propertyClass = JavaPsiFacade.getInstance(project).findClass(JavaFxCommonNames.JAVAFX_BEANS_PROPERTY,
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

  @Nullable
  private static PsiType getDefaultPropertyExpectedType(@Nullable PsiClass aClass) {
    if (aClass == null) return null;
    return CachedValuesManager.getCachedValue(aClass, () -> {
      final PsiAnnotation annotation =
        AnnotationUtil.findAnnotationInHierarchy(aClass, Collections.singleton(JavaFxCommonNames.JAVAFX_BEANS_DEFAULT_PROPERTY));
      if (annotation != null) {
        final PsiAnnotationMemberValue memberValue = annotation.findAttributeValue(null);
        if (memberValue != null) {
          final String propertyName = StringUtil.unquoteString(memberValue.getText());
          final PsiMethod getter = findPropertyGetter(aClass, propertyName);
          if (getter != null) {
            final PsiType propertyType = eraseFreeTypeParameters(getter.getReturnType(), getter);
            return CachedValueProvider.Result.create(propertyType, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
          }
        }
      }
      return CachedValueProvider.Result.create(null, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
    });
  }

  public static String getDefaultPropertyName(@Nullable PsiClass aClass) {
    if (aClass == null) {
      return null;
    }
    final PsiAnnotation annotation = AnnotationUtil.findAnnotationInHierarchy(aClass,
                                                                              Collections.singleton(
                                                                                JavaFxCommonNames.JAVAFX_BEANS_DEFAULT_PROPERTY));
    if (annotation != null) {
      final PsiAnnotationMemberValue memberValue = annotation.findAttributeValue(null);
      if (memberValue != null) {
        return StringUtil.unquoteString(memberValue.getText());
      }
    }
    return null;
  }

  public static boolean isAbleToInstantiate(@NotNull PsiClass psiClass) {
    return isAbleToInstantiate(psiClass, message -> {
    });
  }

  public static boolean isAbleToInstantiate(@NotNull PsiClass psiClass, @NotNull Consumer<String> messageConsumer) {
    if (psiClass.isEnum() || hasNamedArgOrNoArgConstructor(psiClass)) return true;
    final PsiMethod valueOf = findValueOfMethod(psiClass);
    if (valueOf == null) {
      if (!hasBuilder(psiClass)) {
        messageConsumer.accept("Unable to instantiate");
        return false;
      }
    }
    return true;
  }

  private static boolean hasNamedArgOrNoArgConstructor(@NotNull PsiClass psiClass) {
    if (psiClass.getConstructors().length == 0) return true;
    return CachedValuesManager.getCachedValue(psiClass, () -> {
      for (PsiMethod constructor : psiClass.getConstructors()) {
        final PsiParameter[] parameters = constructor.getParameterList().getParameters();
        if (parameters.length == 0) {
          return CachedValueProvider.Result.create(true, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
        }
        boolean annotated = true;
        for (PsiParameter parameter : parameters) {
          if (!AnnotationUtil.isAnnotated(parameter, JavaFxCommonNames.JAVAFX_BEANS_NAMED_ARG, false)) {
            annotated = false;
            break;
          }
        }
        if (annotated) return CachedValueProvider.Result.create(true, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
      }
      return CachedValueProvider.Result.create(false, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
    });
  }

  public static boolean hasBuilder(@NotNull final PsiClass psiClass) {
    return CachedValuesManager.getCachedValue(psiClass, () -> {
      final Project project = psiClass.getProject();
      final PsiClass builderClass = JavaPsiFacade.getInstance(project).findClass(JavaFxCommonNames.JAVAFX_FXML_BUILDER,
                                                                                 GlobalSearchScope.allScope(project));
      if (builderClass != null) {
        final PsiMethod[] buildMethods = builderClass.findMethodsByName("build", false);
        if (buildMethods.length == 1 && buildMethods[0].getParameterList().getParametersCount() == 0) {
          if (ClassInheritorsSearch.search(builderClass).forEach(aClass -> {
            PsiType returnType = null;
            final PsiMethod method = MethodSignatureUtil.findMethodBySuperMethod(aClass, buildMethods[0], false);
            if (method != null) {
              returnType = method.getReturnType();
            }
            return !Comparing.equal(psiClass, PsiUtil.resolveClassInClassTypeOnly(returnType));
          })) {
            return CachedValueProvider.Result.create(false, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
          }
        }
      }
      return CachedValueProvider.Result.create(true, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
    });
  }

  public static boolean isClassAcceptable(@Nullable XmlTag targetTag, @Nullable final PsiClass fromClass) {
    return isClassAcceptable(targetTag, fromClass, (message, type) -> {
    });
  }

  public static boolean isClassAcceptable(@Nullable XmlTag targetTag, @Nullable final PsiClass fromClass,
                                          @NotNull BiConsumer<String, Validator.ValidationHost.ErrorType> messageConsumer) {
    if (targetTag == null || fromClass == null || !fromClass.isValid()) {
      return true;
    }
    final XmlElementDescriptor tagDescriptor = targetTag.getDescriptor();
    if (tagDescriptor instanceof JavaFxPropertyTagDescriptor) {
      final PsiClass containingClass = ((JavaFxPropertyTagDescriptor)tagDescriptor).getPsiClass();
      final PsiType targetType = getWritablePropertyType(containingClass, tagDescriptor.getDeclaration());
      return canCoerce(targetType, fromClass, targetTag, messageConsumer);
    }
    else if (tagDescriptor instanceof JavaFxClassTagDescriptorBase) {
      final PsiElement tagDeclaration = tagDescriptor.getDeclaration();
      if (tagDeclaration instanceof PsiClass) {
        PsiClass defaultPropertyOwnerClass = (PsiClass)tagDeclaration;
        final XmlAttribute factoryAttr = targetTag.getAttribute(FxmlConstants.FX_FACTORY);
        if (factoryAttr != null) {
          defaultPropertyOwnerClass = getFactoryProducedClass((PsiClass)tagDeclaration, factoryAttr.getValue());
        }
        final PsiType targetType = getDefaultPropertyExpectedType(defaultPropertyOwnerClass);
        if (targetType != null) {
          return canCoerce(targetType, fromClass, targetTag, messageConsumer);
        }
        if (!isObservableCollection(defaultPropertyOwnerClass)) {
          return noDefaultPropertyError(messageConsumer);
        }
      }
    }
    return true;
  }

  private static boolean noDefaultPropertyError(@NotNull BiConsumer<String, Validator.ValidationHost.ErrorType> messageConsumer) {
    messageConsumer.accept("Parent tag has no default property",
                           Validator.ValidationHost.ErrorType.ERROR);
    return false;
  }

  private static boolean canCoerce(@Nullable PsiType targetType, @NotNull PsiClass fromClass, @NotNull PsiElement context,
                                   @NotNull BiConsumer<String, Validator.ValidationHost.ErrorType> messageConsumer) {
    if (targetType == null) return true;
    PsiType collectionItemType = JavaGenericsUtil.getCollectionItemType(targetType, fromClass.getResolveScope());
    if (collectionItemType == null && InheritanceUtil.isInheritor(targetType, JavaFxCommonNames.JAVAFX_BEANS_PROPERTY)) {
      collectionItemType = getWritablePropertyType(targetType, fromClass.getProject());
    }
    if (collectionItemType != null) {
      return canCoerceImpl(collectionItemType, fromClass, context, messageConsumer);
    }
    return canCoerceImpl(targetType, fromClass, context, messageConsumer);
  }

  @Nullable
  private static PsiType eraseFreeTypeParameters(@Nullable PsiType psiType, @NotNull PsiMember member) {
    final PsiClass containingClass = member.getContainingClass();
    return eraseFreeTypeParameters(psiType, containingClass);
  }

  @Nullable
  private static PsiType eraseFreeTypeParameters(@Nullable PsiType psiType, @Nullable PsiClass containingClass) {
    if (containingClass == null) return null;
    return JavaPsiFacade.getElementFactory(containingClass.getProject()).createRawSubstitutor(containingClass).substitute(psiType);
  }

  private static boolean canCoerceImpl(@NotNull PsiType targetType, @NotNull PsiClass fromClass, @NotNull PsiElement context,
                                       @NotNull BiConsumer<String, Validator.ValidationHost.ErrorType> messageConsumer) {
    if (targetType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) ||
        targetType.equalsToText(CommonClassNames.JAVA_LANG_STRING) ||
        targetType.isAssignableFrom(PsiTypesUtil.getClassType(fromClass))) {
      return true;
    }
    final PsiClassType boxedTargetClass =
      targetType instanceof PsiPrimitiveType ? ((PsiPrimitiveType)targetType).getBoxedType(context) : null;
    if (boxedTargetClass != null && InheritanceUtil.isInheritor(boxedTargetClass, CommonClassNames.JAVA_LANG_NUMBER) ||
        InheritanceUtil.isInheritor(targetType, CommonClassNames.JAVA_LANG_NUMBER)) {
      if (Comparing.strEqual(fromClass.getQualifiedName(), CommonClassNames.JAVA_LANG_STRING) ||
          InheritanceUtil.isInheritor(fromClass, CommonClassNames.JAVA_LANG_NUMBER)) {
        return true;
      }
      return unrelatedTypesWarning(targetType, fromClass, messageConsumer);
    }
    final PsiMethod valueOfMethod = findValueOfMethod(targetType);
    final PsiType valueOfParameterType = valueOfMethod != null && valueOfMethod.getParameterList().getParametersCount() == 1 ?
                                         valueOfMethod.getParameterList().getParameters()[0].getType() : null;
    if (valueOfParameterType != null && valueOfParameterType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
      return true;
    }
    if (Comparing.strEqual(fromClass.getQualifiedName(), CommonClassNames.JAVA_LANG_STRING)) {
      if (isPrimitiveOrBoxed(targetType) ||
          valueOfParameterType != null && valueOfParameterType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        return true;
      }
    }
    if (valueOfMethod != null) {
      return unrelatedTypesWarning(targetType, fromClass, messageConsumer);
    }
    return unableToCoerceError(targetType, fromClass, messageConsumer);
  }

  private static boolean unableToCoerceError(@NotNull PsiType targetType, @NotNull PsiClass fromClass,
                                             @NotNull BiConsumer<String, Validator.ValidationHost.ErrorType> messageConsumer) {
    messageConsumer.accept("Unable to coerce " + HighlightUtil.formatClass(fromClass) + " to " + targetType.getCanonicalText(),
                           Validator.ValidationHost.ErrorType.ERROR);
    return false;
  }

  private static boolean unrelatedTypesWarning(@NotNull PsiType targetType, @NotNull PsiClass fromClass,
                                               @NotNull BiConsumer<String, Validator.ValidationHost.ErrorType> messageConsumer) {
    messageConsumer.accept("Conversion between unrelated types, " + HighlightUtil.formatClass(fromClass) +
                           " to " + targetType.getCanonicalText(),
                           Validator.ValidationHost.ErrorType.WARNING);
    return true;
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
    return CachedValuesManager.getCachedValue(field, () -> {
      final PsiType fieldType = field.getType();
      final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(fieldType);
      final PsiClass fieldClass = resolveResult.getElement();
      if (fieldClass == null) {
        final PsiType propertyType = eraseFreeTypeParameters(fieldType, field);
        return CachedValueProvider.Result.create(propertyType, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
      }
      PsiType substitute = null;
      for (String typeName : typeMap.keySet()) {
        if (InheritanceUtil.isInheritor(fieldType, typeName)) {
          substitute = typeMap.get(typeName);
          break;
        }
      }
      if (substitute == null) {
        if (!InheritanceUtil.isInheritor(fieldType, JavaFxCommonNames.JAVAFX_BEANS_VALUE_OBSERVABLE_VALUE)) {
          final PsiType propertyType = eraseFreeTypeParameters(fieldType, field);
          return CachedValueProvider.Result.create(propertyType, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
        }
        final PsiClass aClass = JavaPsiFacade.getInstance(project)
          .findClass(JavaFxCommonNames.JAVAFX_BEANS_VALUE_OBSERVABLE_VALUE, GlobalSearchScope.allScope(project));
        LOG.assertTrue(aClass != null);
        final PsiSubstitutor substitutor =
          TypeConversionUtil.getSuperClassSubstitutor(aClass, fieldClass, resolveResult.getSubstitutor());
        final PsiMethod[] values = aClass.findMethodsByName(JavaFxCommonNames.GET_VALUE, false);
        LOG.assertTrue(values.length == 1);
        substitute = substitutor.substitute(values[0].getReturnType());
      }

      final PsiType propertyType = eraseFreeTypeParameters(substitute, field);
      return CachedValueProvider.Result.create(propertyType, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
    });
  }

  @Nullable
  public static PsiType getWritablePropertyType(@Nullable PsiClass containingClass, @Nullable PsiElement declaration) {
    if (declaration instanceof PsiField) {
      return getWrappedPropertyType((PsiField)declaration, declaration.getProject(), JavaFxCommonNames.ourWritableMap);
    }
    if (declaration instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)declaration;
      if (method.getParameterList().getParametersCount() != 0) {
        return getSetterArgumentType(method);
      }
      final String propertyName = PropertyUtil.getPropertyName(method);
      final PsiClass psiClass = containingClass != null ? containingClass : method.getContainingClass();
      if (propertyName != null && containingClass != null) {
        final PsiMethod setter = findInstancePropertySetter(psiClass, propertyName);
        if (setter != null) {
          final PsiType setterArgumentType = getSetterArgumentType(setter);
          if (setterArgumentType != null) return setterArgumentType;
        }
      }
      return getGetterReturnType(method);
    }
    return null;
  }

  @Nullable
  private static PsiType getSetterArgumentType(@NotNull PsiMethod method) {
    return CachedValuesManager.getCachedValue(method, () -> {
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      final boolean isStatic = method.hasModifierProperty(PsiModifier.STATIC);
      if (isStatic && parameters.length == 2 || !isStatic && parameters.length == 1) {
        final PsiType argumentType = eraseFreeTypeParameters(parameters[parameters.length - 1].getType(), method);
        return CachedValueProvider.Result.create(argumentType, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
      }
      return CachedValueProvider.Result.create(null, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
    });
  }

  private static PsiType getGetterReturnType(@NotNull PsiMethod method) {
    return CachedValuesManager.getCachedValue(method, () -> {
      final PsiType returnType = eraseFreeTypeParameters(method.getReturnType(), method);
      return CachedValueProvider.Result.create(returnType, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
    });
  }


  @Nullable
  public static PsiType getReadablePropertyType(@Nullable PsiElement declaration) {
    if (declaration instanceof PsiField) {
      return getWrappedPropertyType((PsiField)declaration, declaration.getProject(), JavaFxCommonNames.ourReadOnlyMap);
    }
    if (declaration instanceof PsiMethod) {
      PsiMethod psiMethod = (PsiMethod)declaration;
      if (psiMethod.getParameterList().getParametersCount() == 0 &&
          !psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
        return getGetterReturnType(psiMethod);
      }
    }
    return null;
  }

  @NotNull
  public static Map<String, XmlAttributeValue> collectFileIds(@Nullable final XmlTag currentTag) {
    if (currentTag == null) return Collections.emptyMap();
    final PsiFile containingFile = currentTag.getContainingFile();
    final XmlAttribute currentIdAttribute = currentTag.getAttribute(FxmlConstants.FX_ID);
    return collectFileIds(containingFile, currentIdAttribute != null ? currentIdAttribute.getValue() : null);
  }

  @NotNull
  public static Map<String, XmlAttributeValue> collectFileIds(@Nullable PsiFile psiFile, @Nullable String skipFxId) {
    if (!(psiFile instanceof XmlFile)) return Collections.emptyMap();
    final XmlTag rootTag = ((XmlFile)psiFile).getRootTag();
    if (rootTag == null) return Collections.emptyMap();

    final Map<String, XmlAttributeValue> cachedIds = CachedValuesManager
      .getCachedValue(rootTag, () -> new CachedValueProvider.Result<>(prepareFileIds(rootTag), PsiModificationTracker.MODIFICATION_COUNT));
    if (skipFxId != null && cachedIds.containsKey(skipFxId)) {
      final Map<String, XmlAttributeValue> filteredIds = new THashMap<>(cachedIds);
      filteredIds.remove(skipFxId);
      return filteredIds;
    }
    return cachedIds;
  }

  @NotNull
  private static Map<String, XmlAttributeValue> prepareFileIds(XmlTag rootTag) {
    final Map<String, XmlAttributeValue> fileIds = new THashMap<>();
    for (XmlTag tag : SyntaxTraverser.psiTraverser().withRoot(rootTag).filter(XmlTag.class)) {
      final XmlAttribute idAttribute = tag.getAttribute(FxmlConstants.FX_ID);
      if (idAttribute != null) {
        final String idValue = idAttribute.getValue();
        if (idValue != null) fileIds.put(idValue, idAttribute.getValueElement());
      }
    }
    final XmlAttribute controllerAttribute = rootTag.getAttribute(FxmlConstants.FX_CONTROLLER);
    if (controllerAttribute != null) {
      fileIds.put(FxmlConstants.CONTROLLER, controllerAttribute.getValueElement());
    }
    return fileIds;
  }

  @Nullable
  public static PsiClass getTagClassById(@Nullable XmlAttributeValue xmlAttributeValue, @Nullable String id, @NotNull PsiElement context) {
    return FxmlConstants.CONTROLLER.equals(id) ? getControllerClass(context.getContainingFile()) : getTagClass(xmlAttributeValue);
  }

  @Nullable
  public static PsiClass getWritablePropertyClass(@Nullable XmlAttributeValue xmlAttributeValue) {
    if (xmlAttributeValue != null) {
      return getPropertyClass(getWritablePropertyType(xmlAttributeValue), xmlAttributeValue);
    }
    return null;
  }

  @Nullable
  public static PsiType getWritablePropertyType(@Nullable XmlAttributeValue xmlAttributeValue) {
    final PsiClass tagClass = getTagClass(xmlAttributeValue);
    if (tagClass != null) {
      final PsiElement declaration = getAttributeDeclaration(xmlAttributeValue);
      if (declaration != null) {
        return getWritablePropertyType(tagClass, declaration);
      }
    }
    return null;
  }

  @Nullable
  public static PsiClass getPropertyClass(@Nullable PsiType propertyType, @NotNull PsiElement context) {
    if (propertyType instanceof PsiPrimitiveType) {
      PsiClassType boxedType = ((PsiPrimitiveType)propertyType).getBoxedType(context);
      return boxedType != null ? boxedType.resolve() : null;
    }
    return PsiUtil.resolveClassInType(propertyType);
  }

  public static boolean hasConversionFromAnyType(@NotNull PsiClass targetClass) {
    return Comparing.strEqual(targetClass.getQualifiedName(), CommonClassNames.JAVA_LANG_STRING)
           || findValueOfMethod(targetClass) != null;
  }

  @Nullable
  public static String getBoxedPropertyType(@Nullable PsiClass containingClass, @Nullable PsiMember declaration) {
    PsiType psiType = getWritablePropertyType(containingClass, declaration);
    if (psiType instanceof PsiPrimitiveType) {
      return ((PsiPrimitiveType)psiType).getBoxedTypeName();
    }
    if (PsiPrimitiveType.getUnboxedType(psiType) != null) {
      final PsiClass psiClass = PsiUtil.resolveClassInType(psiType);
      if (psiClass != null) {
        return psiClass.getQualifiedName();
      }
    }
    return null;
  }

  @Contract("null->false")
  public static boolean isPrimitiveOrBoxed(@Nullable PsiType psiType) {
    return psiType instanceof PsiPrimitiveType || PsiPrimitiveType.getUnboxedType(psiType) != null;
  }

  @NotNull
  public static Map<String, PsiMember> collectReadableProperties(@Nullable PsiClass psiClass) {
    if (psiClass != null) {
      return CachedValuesManager.getCachedValue(psiClass, () ->
        CachedValueProvider.Result.create(prepareReadableProperties(psiClass), PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT));
    }
    return Collections.emptyMap();
  }


  @NotNull
  private static Map<String, PsiMember> prepareReadableProperties(@NotNull PsiClass psiClass) {
    final Map<String, PsiMember> acceptableMembers = new THashMap<>();
    for (PsiMethod method : psiClass.getAllMethods()) {
      if (method.hasModifierProperty(PsiModifier.STATIC) || !method.hasModifierProperty(PsiModifier.PUBLIC)) continue;
      if (PropertyUtil.isSimplePropertyGetter(method)) {
        final String propertyName = PropertyUtil.getPropertyName(method);
        assert propertyName != null;
        acceptableMembers.put(propertyName, method);
      }
    }
    return acceptableMembers;
  }

  @NotNull
  public static Map<String, PsiMember> collectWritableProperties(@Nullable PsiClass psiClass) {
    if (psiClass != null) {
      return CachedValuesManager.getCachedValue(psiClass, () ->
        CachedValueProvider.Result.create(prepareWritableProperties(psiClass), PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT));
    }
    return Collections.emptyMap();
  }

  @NotNull
  private static Map<String, PsiMember> prepareWritableProperties(@NotNull PsiClass psiClass) {
    // todo search for setter in corresponding builder class, e.g. MyDataBuilder.setText() + MyData.getText(), reuse logic from hasBuilder()
    final Map<String, PsiMember> acceptableMembers = new THashMap<>();
    for (PsiMethod constructor : psiClass.getConstructors()) {
      if (!constructor.hasModifierProperty(PsiModifier.PUBLIC)) continue;
      final PsiParameter[] parameters = constructor.getParameterList().getParameters();
      for (PsiParameter parameter : parameters) {
        String propertyName = getPropertyNameFromNamedArgAnnotation(parameter);
        if (propertyName != null && !acceptableMembers.containsKey(propertyName)) {
          final PsiField field = psiClass.findFieldByName(propertyName, true);
          if (field != null && !field.hasModifierProperty(PsiModifier.STATIC)) {
            acceptableMembers.put(propertyName, field);
          }
        }
      }
    }
    for (PsiMethod method : psiClass.getAllMethods()) {
      if (method.hasModifierProperty(PsiModifier.STATIC) || !method.hasModifierProperty(PsiModifier.PUBLIC)) continue;
      if (PropertyUtil.isSimplePropertyGetter(method)) {
        PsiMember acceptableMember = method;
        final String propertyName = PropertyUtil.getPropertyName(method);
        assert propertyName != null;

        PsiMethod setter = findInstancePropertySetter(psiClass, propertyName);
        if (setter != null) {
          final PsiType setterArgType = setter.getParameterList().getParameters()[0].getType();
          final PsiField field = psiClass.findFieldByName(propertyName, true);
          if (field != null && !field.hasModifierProperty(PsiModifier.STATIC)) {
            final PsiType fieldType = getWritablePropertyType(psiClass, field);
            if (fieldType == null || setterArgType.isConvertibleFrom(fieldType)) {
              acceptableMember = field;
            }
          }
        }
        else {
          final PsiType returnType = method.getReturnType();
          if (returnType != null && isWritablePropertyType(psiClass, returnType)) {
            final PsiField field = psiClass.findFieldByName(propertyName, true);
            if (field != null && !field.hasModifierProperty(PsiModifier.STATIC)) {
              final PsiType fieldType = getWritablePropertyType(psiClass, field);
              if (fieldType == null || returnType.isAssignableFrom(fieldType)) {
                acceptableMember = field;
              }
            }
          }
          else {
            acceptableMember = null;
          }
        }
        if (acceptableMember != null) acceptableMembers.put(propertyName, acceptableMember);
      }
    }
    return acceptableMembers;
  }

  @Nullable
  private static String getPropertyNameFromNamedArgAnnotation(@NotNull PsiParameter parameter) {
    final PsiAnnotation annotation = AnnotationUtil.findAnnotation(parameter, JavaFxCommonNames.JAVAFX_BEANS_NAMED_ARG);
    if (annotation != null) {
      final PsiAnnotationMemberValue psiValue = annotation.findAttributeValue(JavaFxCommonNames.VALUE);
      if (psiValue instanceof PsiLiteralExpression) {
        final Object value = ((PsiLiteralExpression)psiValue).getValue();
        if (value instanceof String) {
          return (String)value;
        }
      }
    }
    return null;
  }

  @Nullable
  public static PsiMethod findInstancePropertySetter(@NotNull PsiClass psiClass, @Nullable String propertyName) {
    if (StringUtil.isEmpty(propertyName)) return null;
    final String suggestedSetterName = PropertyUtil.suggestSetterName(propertyName);
    final PsiMethod[] setters = psiClass.findMethodsByName(suggestedSetterName, true);
    for (PsiMethod setter : setters) {
      if (setter.hasModifierProperty(PsiModifier.PUBLIC) &&
          !setter.hasModifierProperty(PsiModifier.STATIC) &&
          PropertyUtil.isSimplePropertySetter(setter)) {
        return setter;
      }
    }
    return null;
  }

  private static boolean isWritablePropertyType(@NotNull PsiClass psiClass, @NotNull PsiType fieldType) {
    return isObservableCollection(PsiUtil.resolveClassInType(fieldType)) &&
           JavaGenericsUtil.getCollectionItemType(fieldType, psiClass.getResolveScope()) != null ||
           InheritanceUtil.isInheritor(fieldType, JavaFxCommonNames.JAVAFX_COLLECTIONS_OBSERVABLE_MAP);
  }

  public static boolean isObservableCollection(@Nullable PsiClass psiClass) {
    return psiClass != null &&
           (InheritanceUtil.isInheritor(psiClass, JavaFxCommonNames.JAVAFX_COLLECTIONS_OBSERVABLE_LIST) ||
            InheritanceUtil.isInheritor(psiClass, JavaFxCommonNames.JAVAFX_COLLECTIONS_OBSERVABLE_SET) ||
            InheritanceUtil.isInheritor(psiClass, JavaFxCommonNames.JAVAFX_COLLECTIONS_OBSERVABLE_ARRAY));
  }

  @Nullable
  private static PsiSubstitutor getTagClassSubstitutor(@NotNull XmlAttribute xmlAttribute, @NotNull PsiClass controllerClass) {
    final XmlTag xmlTag = xmlAttribute.getParent();
    final PsiClass tagClass = getTagClass(xmlTag);
    if (tagClass != null) {
      final String tagFieldName = xmlTag.getAttributeValue(FxmlConstants.FX_ID);
      if (!StringUtil.isEmpty(tagFieldName)) {
        final PsiField tagField = controllerClass.findFieldByName(tagFieldName, true);
        if (tagField != null && !tagField.hasModifierProperty(PsiModifier.STATIC) && isVisibleInFxml(tagField)) {
          final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(tagField.getType());
          final PsiClass resolvedClass = resolveResult.getElement();
          if (resolvedClass != null) {
            return TypeConversionUtil.getClassSubstitutor(tagClass, resolvedClass, resolveResult.getSubstitutor());
          }
        }
      }
    }
    return null;
  }

  @Nullable
  public static PsiClassType getDeclaredEventType(@NotNull XmlAttribute xmlAttribute) {
    final PsiClass tagClass = getTagClass(xmlAttribute.getParent());
    if (tagClass != null) {
      final PsiType eventHandlerPropertyType = getEventHandlerPropertyType(tagClass, xmlAttribute.getName());
      if (eventHandlerPropertyType != null) {
        final PsiClass controllerClass = getControllerClass(xmlAttribute.getContainingFile());
        if (controllerClass != null) {
          final PsiSubstitutor tagClassSubstitutor = getTagClassSubstitutor(xmlAttribute, controllerClass);

          final PsiType handlerType = tagClassSubstitutor != null ?
                                      tagClassSubstitutor.substitute(eventHandlerPropertyType) : eventHandlerPropertyType;
          final PsiClassType eventType = substituteEventType(handlerType, xmlAttribute.getProject());
          final PsiType erasedType = eraseFreeTypeParameters(eventType, tagClass);
          return erasedType instanceof PsiClassType ? (PsiClassType)erasedType : null;
        }
      }
    }
    return null;
  }

  @Nullable
  private static PsiType getEventHandlerPropertyType(@NotNull PsiClass tagClass, @NotNull String eventName) {
    final PsiMethod[] handlerSetterCandidates = tagClass.findMethodsByName(PropertyUtil.suggestSetterName(eventName), true);
    for (PsiMethod handlerSetter : handlerSetterCandidates) {
      if (!handlerSetter.hasModifierProperty(PsiModifier.STATIC) &&
          handlerSetter.hasModifierProperty(PsiModifier.PUBLIC)) {
        final PsiType propertyType = PropertyUtil.getPropertyType(handlerSetter);
        if (InheritanceUtil.isInheritor(propertyType, JavaFxCommonNames.JAVAFX_EVENT_EVENT_HANDLER)) {
          return propertyType;
        }
      }
    }
    final PsiField handlerField = tagClass.findFieldByName(eventName, true);
    final PsiClassType propertyType = getPropertyClassType(handlerField);
    if (InheritanceUtil.isInheritor(propertyType, JavaFxCommonNames.JAVAFX_EVENT_EVENT_HANDLER)) {
      return propertyType;
    }
    return null;
  }

  @Nullable
  private static PsiClassType substituteEventType(@Nullable PsiType eventHandlerType, @NotNull Project project) {
    if (!(eventHandlerType instanceof PsiClassType)) return null;
    final PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)eventHandlerType).resolveGenerics();
    final PsiClass eventHandlerClass = resolveResult.getElement();
    if (eventHandlerClass == null) return null;
    final PsiSubstitutor eventHandlerClassSubstitutor = resolveResult.getSubstitutor();

    final PsiClass eventHandlerInterface =
      JavaPsiFacade.getInstance(project).findClass(JavaFxCommonNames.JAVAFX_EVENT_EVENT_HANDLER, GlobalSearchScope.allScope(project));
    if (eventHandlerInterface == null) return null;
    if (!InheritanceUtil.isInheritorOrSelf(eventHandlerClass, eventHandlerInterface, true)) return null;
    final PsiTypeParameter[] typeParameters = eventHandlerInterface.getTypeParameters();
    if (typeParameters.length != 1) return null;
    final PsiTypeParameter eventTypeParameter = typeParameters[0];
    final PsiSubstitutor substitutor =
      TypeConversionUtil.getSuperClassSubstitutor(eventHandlerInterface, eventHandlerClass, eventHandlerClassSubstitutor);
    final PsiType eventType = substitutor.substitute(eventTypeParameter);
    if (eventType instanceof PsiClassType) {
      return (PsiClassType)eventType;
    }
    if (eventType instanceof PsiWildcardType) { // TODO Handle wildcards more accurately
      final PsiType boundType = ((PsiWildcardType)eventType).getBound();
      if (boundType instanceof PsiClassType) {
        return (PsiClassType)boundType;
      }
    }
    return null;
  }

  @Nullable
  private static PsiClass getFactoryProducedClass(@Nullable PsiClass psiClass, @Nullable String factoryMethodName) {
    if (psiClass == null || factoryMethodName == null) return null;
    final PsiMethod[] methods = psiClass.findMethodsByName(factoryMethodName, true);
    for (PsiMethod method : methods) {
      if (method.getParameterList().getParametersCount() == 0 &&
          method.hasModifierProperty(PsiModifier.STATIC)) {
        return PsiUtil.resolveClassInClassTypeOnly(method.getReturnType());
      }
    }
    return null;
  }

  @Nullable
  public static String validateEnumConstant(@NotNull PsiClass enumClass, @NonNls @Nullable String name) {
    if (!enumClass.isEnum() || name == null) return null;
    final Set<String> constantNames = CachedValuesManager.getCachedValue(enumClass, () ->
      CachedValueProvider.Result.create(Arrays.stream(enumClass.getFields())
                                          .filter(PsiEnumConstant.class::isInstance)
                                          .map(PsiField::getName)
                                          .map(String::toUpperCase)
                                          .collect(Collectors.toCollection(THashSet::new)),
                                        PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT));
    if (!constantNames.contains(name.toUpperCase())) {
      return "No enum constant '" + name + "' in " + enumClass.getQualifiedName();
    }
    return null;
  }

  @NotNull
  public static String getPropertyName(@NotNull String memberName, boolean isMethod) {
    if (!isMethod) return memberName;
    final String propertyName = PropertyUtil.getPropertyName(memberName);
    return propertyName != null ? propertyName : memberName;
  }

  @Nullable
  public static PsiClass getTagValueClass(@NotNull XmlTag xmlTag) {
    return getTagValueClass(xmlTag, getTagClass(xmlTag)).getFirst();
  }

  @NotNull
  public static Pair<PsiClass, Boolean> getTagValueClass(@NotNull XmlTag xmlTag, @Nullable PsiClass tagClass) {
    if (tagClass != null) {
      final XmlAttribute constAttr = xmlTag.getAttribute(FxmlConstants.FX_CONSTANT);
      if (constAttr != null) {
        final PsiField constField = tagClass.findFieldByName(constAttr.getValue(), true);
        if (constField != null) {
          final PsiType constType = constField.getType();
          return Pair.create(PsiUtil.resolveClassInClassTypeOnly(
            constType instanceof PsiPrimitiveType ? ((PsiPrimitiveType)constType).getBoxedType(xmlTag) : constType), true);
        }
      }
      else {
        final XmlAttribute factoryAttr = xmlTag.getAttribute(FxmlConstants.FX_FACTORY);
        if (factoryAttr != null) {
          return Pair.create(getFactoryProducedClass(tagClass, factoryAttr.getValue()), true);
        }
      }
    }
    return Pair.create(tagClass, false);
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
      final Ref<PsiClass> injectedController = new Ref<>();
      final Ref<PsiFile> dep = new Ref<>();
      final PsiClass fxmlLoader =
        JavaPsiFacade.getInstance(myProject).findClass(JavaFxCommonNames.JAVAFX_FXML_FXMLLOADER, GlobalSearchScope.allScope(myProject));
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
          ReferencesSearch.search(myContainingFile, globalSearchScope).forEach(reference -> {
            final PsiElement element = reference.getElement();
            if (element instanceof PsiLiteralExpression) {
              final PsiNewExpression expression = PsiTreeUtil.getParentOfType(element, PsiNewExpression.class);
              if (expression != null) {
                final PsiType type = expression.getType();
                if (type != null && type.equalsToText(JavaFxCommonNames.JAVAFX_FXML_FXMLLOADER)) {
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
          });
        }
      }
      return new Result<>(injectedController.get(), dep.get() != null ? dep.get() : PsiModificationTracker.MODIFICATION_COUNT);
    }

    private static abstract class JavaFxRetrieveControllerProcessor implements Processor<PsiReference> {
      private final Ref<PsiClass> myInjectedController = new Ref<>();
      private final Ref<PsiFile> myContainingFile = new Ref<>();

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
