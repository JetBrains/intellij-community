package org.jetbrains.plugins.javaFX.codeInsight;

import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.typeMigration.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonNames;
import org.jetbrains.plugins.javaFX.fxml.JavaFxModuleUtil;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

import java.util.Collection;
import java.util.Set;

/**
 * @author Pavel.Dolgov
 */
public class JavaFxFieldToPropertyIntention extends PsiElementBaseIntentionAction implements LowPriorityAction {
  private static final Logger LOG = Logger.getInstance(JavaFxFieldToPropertyIntention.class);
  public static final String FAMILY_NAME = "Convert to JavaFX property";

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return FAMILY_NAME;
  }

  @NotNull
  @Override
  public String getText() {
    //noinspection DialogTitleCapitalization
    return FAMILY_NAME;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    final PsiField field = getField(element);
    if (field != null) {
      final PsiFile file = field.getContainingFile();
      if (JavaFxModuleUtil.isInJavaFxProject(file) || JavaFxPsiUtil.isJavaFxPackageImported(file)) {
        return PropertyInfo.createPropertyInfo(field, project) != null;
      }
    }
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    final PsiField field = getField(element);
    LOG.assertTrue(field != null, "field");
    final PropertyInfo property = PropertyInfo.createPropertyInfo(field, project);
    LOG.assertTrue(property != null, "propertyInfo");
    new SearchUsagesTask(project, property).queue();
  }

  private static class SearchUsagesTask extends Task.Modal {
    private final PropertyInfo myProperty;
    private Collection<PsiReference> myReferences;
    private Set<PsiFile> myFiles;

    public SearchUsagesTask(@NotNull Project project,
                            @NotNull PropertyInfo property) {
      super(project, "Searching for usages of '" + property.myFieldName + "'", true);
      myProperty = property;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      ReadAction.run(() -> {
        myReferences = ReferencesSearch.search(myProperty.myField).findAll();

        final Set<PsiElement> occurrences = new THashSet<>();
        occurrences.add(myProperty.myField);
        occurrences.addAll(ContainerUtil.mapNotNull(myReferences, PsiReference::getElement));

        myFiles = ContainerUtil.map2SetNotNull(occurrences, element -> {
          final PsiFile file = element.getContainingFile();
          return file != null && file.isPhysical() ? file : null;
        });
      });
      WriteCommandAction
        .runWriteCommandAction(myProject, "Convert '" + myProperty.myFieldName + "' to JavaFX property", null,
                               this::replaceOccurrences, myFiles.toArray(PsiFile.EMPTY_ARRAY));
    }

    private void replaceOccurrences() {
      LOG.assertTrue(myProject != null, "myProject");
      final PsiField field = myProperty.myField;
      field.normalizeDeclaration();

      final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myProject).getElementFactory();
      final PsiType fromType = field.getType();
      final PsiType toType = elementFactory.createTypeFromText(myProperty.myObservableType.myText, field);
      try {
        final TypeMigrationRules rules = new TypeMigrationRules(myProject);
        final Set<VirtualFile> virtualFiles = ContainerUtil.map2SetNotNull(myFiles, PsiFile::getVirtualFile);
        rules.setBoundScope(GlobalSearchScope.filesScope(myProject, virtualFiles));
        final TypeMigrationLabeler labeler = new TypeMigrationLabeler(rules, toType, myProject);
        labeler.getMigratedUsages(false, field);

        for (PsiReference reference : myReferences) {
          final PsiElement refElement = reference.getElement();
          if (refElement instanceof PsiExpression) {
            final PsiExpression expression = (PsiExpression)refElement;
            final TypeConversionDescriptor conversion =
              myProperty.myObservableType.findDirectConversion(expression, toType, fromType);
            if (conversion != null) {
              TypeMigrationReplacementUtil.replaceExpression(expression, myProject, conversion, new TypeEvaluator(null, null, myProject));
            }
          }
        }
        myProperty.convertField();
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }

  @Nullable
  private static PsiField getField(@NotNull PsiElement element) {
    if (!(element instanceof PsiIdentifier)) return null;
    final PsiField field = PsiTreeUtil.getParentOfType(element, PsiField.class);
    if (field == null) return null;
    if (field.getLanguage() != JavaLanguage.INSTANCE) return null;
    if (field.getTypeElement() == null) return null;
    if (field.hasModifierProperty(PsiModifier.STATIC) || field.hasModifierProperty(PsiModifier.FINAL)) return null;
    return field;
  }

  private static class PropertyInfo {
    final PsiField myField;
    final PsiClass myContainingClass;
    final PsiTypeElement myTypeElement;
    final String myFieldName;
    final ObservableType myObservableType;

    private PropertyInfo(@NotNull PsiField field,
                         @NotNull PsiClass containingClass,
                         @NotNull PsiTypeElement typeElement,
                         @NotNull String fieldName,
                         @NotNull ObservableType observableType) {
      myField = field;
      myContainingClass = containingClass;
      myTypeElement = typeElement;
      myFieldName = fieldName;
      myObservableType = observableType;
    }

    static PropertyInfo createPropertyInfo(@NotNull PsiField field, @NotNull Project project) {
      final String fieldName = field.getName();
      final PsiClass containingClass = field.getContainingClass();
      final PsiTypeElement typeElement = field.getTypeElement();
      if (fieldName != null && containingClass != null && typeElement != null) {
        final ObservableType observableType = ObservableType.createObservableType(field, project);
        if (observableType != null) {
          return new PropertyInfo(field, containingClass, typeElement, fieldName, observableType);
        }
      }
      return null;
    }

    private void convertField() {
      final Project project = myContainingClass.getProject();
      final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();

      final PsiTypeElement newTypeElement = elementFactory.createTypeElementFromText(myObservableType.myText, myField);
      myTypeElement.replace(newTypeElement);

      final PsiExpression initializer = myField.getInitializer();
      final String propertyName = JavaCodeStyleManager.getInstance(project).variableNameToPropertyName(myFieldName, VariableKind.FIELD);
      final String initializerArgs = "this,\"" + propertyName + "\"" + (initializer == null ? "" : "," + initializer.getText());

      String initializerText = "new " + myObservableType.myText + "(" + initializerArgs + ")";
      final PsiNewExpression newInitializer = (PsiNewExpression)elementFactory.createExpressionFromText(initializerText, myField);
      myField.setInitializer(newInitializer);

      final PsiType fieldType = myField.getType();
      if (PsiDiamondTypeUtil.canCollapseToDiamond(newInitializer, (PsiNewExpression)myField.getInitializer(), fieldType)) {
        final PsiJavaCodeReferenceElement classReference = newInitializer.getClassOrAnonymousClassReference();
        if (classReference != null) {
          PsiDiamondTypeUtil.replaceExplicitWithDiamond(classReference.getParameterList());
        }
      }
      myField.setInitializer(newInitializer);

      final JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);
      final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
      codeStyleManager.reformat(javaCodeStyleManager.shortenClassReferences(myField));
    }
  }

  static class ObservableType {
    final String myText;

    ObservableType(@NotNull String text) {
      this.myText = text;
    }

    @Nullable
    static ObservableType createObservableType(@NotNull PsiField field, @NotNull Project project) {
      final PsiType type = field.getType();
      if (type instanceof PsiPrimitiveType) {
        final String text = JavaFxCommonNames.ourObservablePrimitiveWrappers.get(type);
        return text != null ? new ObservablePrimitive(text, (PsiPrimitiveType)type) : null;
      }
      final PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(type);
      if (unboxedType != null) {
        final String text = JavaFxCommonNames.ourObservablePrimitiveWrappers.get(unboxedType);
        return text != null ? new ObservablePrimitive(text, unboxedType) : null;
      }
      if (type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        return new ObservableString();
      }
      if (type instanceof PsiClassType) {
        if (InheritanceUtil.isInheritor(type, JavaFxCommonNames.JAVAFX_BEANS_OBSERVABLE)) {
          return null; // already observable
        }
        if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_LIST)) {
          return ObservableList.createObservableList(type, project);
        }
        else if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_COLLECTION) ||
                 InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP)) {
          return null; // TODO: support SimpleSetProperty, SimpleMapProperty
        }
        else {
          return new ObservableObject(type);
        }
      }
      return null;
    }

    TypeConversionDescriptor findDirectConversion(@NotNull PsiElement context,
                                                  @NotNull PsiType to,
                                                  @NotNull PsiType from) {
      final PsiClass toTypeClass = PsiUtil.resolveClassInType(to);
      LOG.assertTrue(toTypeClass != null);

      final PsiElement parent = context.getParent();
      if (parent instanceof PsiAssignmentExpression) {
        final PsiAssignmentExpression expression = (PsiAssignmentExpression)parent;
        final IElementType tokenType = expression.getOperationTokenType();
        if (tokenType == JavaTokenType.EQ) {
          return findSimpleAssignmentConversion(expression);
        }
        final String sign = expression.getOperationSign().getText();
        final String binarySign = sign.substring(0, sign.length() - 1);
        return findCompoundAssignmentConversion(from, expression, sign, binarySign);
      }
      else if (parent instanceof PsiPostfixExpression) {
        final PsiPostfixExpression expression = (PsiPostfixExpression)parent;
        final TypeConversionDescriptor conversion = getUpdateConversion(expression, expression.getOperationSign(), true);
        if (conversion != null) return conversion;
      }
      else if (parent instanceof PsiPrefixExpression) {
        final PsiPrefixExpression expression = (PsiPrefixExpression)parent;
        final TypeConversionDescriptor conversion = getUpdateConversion(expression, expression.getOperationSign(), false);
        if (conversion != null) return conversion;
      }
      else if (context instanceof PsiReferenceExpression) {
        final PsiExpression qualifierExpression = ((PsiReferenceExpression)context).getQualifierExpression();
        final PsiExpression expression = context.getParent() instanceof PsiMethodCallExpression && qualifierExpression != null
                                         ? qualifierExpression
                                         : (PsiExpression)context;
        return getReadConversion(expression);
      }

      return null;
    }

    @Nullable
    TypeConversionDescriptor findSimpleAssignmentConversion(PsiAssignmentExpression expression) {
      return new TypeConversionDescriptor("$qualifier$ = $val$", "$qualifier$.set($val$)", expression);
    }

    @Nullable
    TypeConversionDescriptor findCompoundAssignmentConversion(@NotNull PsiType from,
                                                              @NotNull PsiExpression expression,
                                                              @NotNull String sign,
                                                              @NotNull String binarySign) {
      return null;
    }

    @Nullable
    TypeConversionDescriptor getUpdateConversion(@NotNull PsiExpression expression, @NotNull PsiJavaToken operationToken, boolean postfix) {
      return null;
    }


    @NotNull
    TypeConversionDescriptor getReadConversion(PsiExpression expression) {
      return new TypeConversionDescriptor("$qualifier$", "$qualifier$.get()", expression);
    }
  }

  static class ObservablePrimitive extends ObservableType {
    final PsiPrimitiveType myType;

    ObservablePrimitive(@NotNull String text, @NotNull PsiPrimitiveType type) {
      super(text);
      myType = type;
    }

    @Nullable
    @Override
    TypeConversionDescriptor findCompoundAssignmentConversion(@NotNull PsiType from,
                                                              @NotNull PsiExpression expression,
                                                              @NotNull String sign,
                                                              @NotNull String binarySign) {
      final PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(from);
      final String valueType = (unboxedType != null ? unboxedType : from).getCanonicalText();
      return new TypeConversionDescriptor("$qualifier$ " + sign + " $val$",
                                          "$qualifier$.set((" + valueType + ")($qualifier$.get() " + binarySign + " ($val$)))",
                                          expression);
    }

    @Nullable
    @Override
    TypeConversionDescriptor getUpdateConversion(@NotNull PsiExpression expression, @NotNull PsiJavaToken operationToken, boolean postfix) {
      final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(expression);
      if (parent instanceof PsiExpressionStatement) {
        final IElementType tokenType = operationToken.getTokenType();
        if (tokenType == JavaTokenType.PLUSPLUS || tokenType == JavaTokenType.MINUSMINUS) {
          final String sign = operationToken.getText();
          return new TypeConversionDescriptor(postfix ? ("$qualifier$" + sign) : (sign + "$qualifier$"),
                                              "$qualifier$.set($qualifier$.get()" + sign.charAt(0) + "1)", expression);
        }
      }
      return null;
    }
  }

  static class ObservableString extends ObservableType {
    ObservableString() {
      super(JavaFxCommonNames.JAVAFX_BEANS_PROPERTY_SIMPLE_STRING_PROPERTY);
    }

    @Nullable
    @Override
    TypeConversionDescriptor findCompoundAssignmentConversion(@NotNull PsiType from,
                                                              @NotNull PsiExpression expression,
                                                              @NotNull String sign,
                                                              @NotNull String binarySign) {
      return new TypeConversionDescriptor("$qualifier$ " + sign + " $val$",
                                          "$qualifier$.set($qualifier$.get() " + binarySign + " ($val$))",
                                          expression);
    }
  }

  static class ObservableList extends ObservableType {
    final PsiType myOriginalType;
    final PsiType myItemType;
    final Project myProject;

    ObservableList(@NotNull PsiType originalType, @NotNull PsiType itemType, @NotNull Project project) {
      super(JavaFxCommonNames.JAVAFX_BEANS_PROPERTY_SIMPLE_LIST_PROPERTY + "<" + itemType.getCanonicalText() + ">");
      myOriginalType = originalType;
      myItemType = itemType;
      myProject = project;
    }

    @Nullable
    private static ObservableType createObservableList(@NotNull PsiType type, @NotNull Project project) {
      final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(type);
      final PsiClass fieldClass = resolveResult.getElement();
      if (fieldClass != null) {
        final PsiClass listClass = JavaPsiFacade.getInstance(project)
          .findClass(CommonClassNames.JAVA_UTIL_LIST, GlobalSearchScope.allScope(project));
        if (listClass != null) {
          final PsiSubstitutor substitutor =
            TypeConversionUtil.getClassSubstitutor(listClass, fieldClass, resolveResult.getSubstitutor());
          if (substitutor != null) {
            final PsiType itemType = substitutor.substitute(listClass.getTypeParameters()[0]);
            if (itemType != null) {
              return new ObservableList(type, itemType, project);
            }
          }
        }
      }
      return null;
    }

    @Nullable
    @Override
    TypeConversionDescriptor findSimpleAssignmentConversion(PsiAssignmentExpression expression) {
      return new TypeConversionDescriptor("$qualifier$ = $val$", "$qualifier$.setAll($val$)", expression);
    }

    @NotNull
    @Override
    TypeConversionDescriptor getReadConversion(PsiExpression expression) {
      return new TypeConversionDescriptor("$qualifier$", "$qualifier$.get()", expression) {
        @Override
        public PsiExpression replace(PsiExpression expression, @NotNull TypeEvaluator evaluator) {
          final PsiExpression replaced = super.replace(expression, evaluator);
          // Replace the getter's return type: List -> ObservableList
          final PsiElement parent = replaced.getParent();
          if (parent instanceof PsiReturnStatement) {
            final PsiReturnStatement returnStatement = (PsiReturnStatement)parent;
            final PsiElement statementParent = returnStatement.getParent();
            if (statementParent instanceof PsiCodeBlock) {
              final PsiCodeBlock codeBlock = (PsiCodeBlock)statementParent;
              final PsiElement blockParent = codeBlock.getParent();
              if (blockParent instanceof PsiMethod) {
                final PsiMethod method = (PsiMethod)blockParent;
                final PsiTypeElement returnTypeElement = method.getReturnTypeElement();
                if (returnTypeElement != null && myOriginalType.equals(method.getReturnType())) {
                  final String text = JavaFxCommonNames.JAVAFX_COLLECTIONS_OBSERVABLE_LIST + "<" + myItemType.getCanonicalText() + ">";
                  final PsiTypeElement newReturnTypeElement = JavaPsiFacade.getInstance(myProject)
                    .getElementFactory().createTypeElementFromText(text, method);
                  final JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(myProject);
                  javaCodeStyleManager.shortenClassReferences(returnTypeElement.replace(newReturnTypeElement));
                }
              }
            }
          }
          return replaced;
        }
      };
    }
  }

  static class ObservableObject extends ObservableType {
    final PsiType myType;

    ObservableObject(@NotNull PsiType type) {
      super(JavaFxCommonNames.JAVAFX_BEANS_PROPERTY_SIMPLE_OBJECT_PROPERTY + "<" + type.getCanonicalText() + ">");
      myType = type;
    }
  }
}
