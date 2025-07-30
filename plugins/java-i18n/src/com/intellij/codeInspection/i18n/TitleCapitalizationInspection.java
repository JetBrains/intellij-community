// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.i18n;

import com.ibm.icu.text.MessagePattern;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.restriction.AnnotationContext;
import com.intellij.codeInspection.restriction.StringFlowUtil;
import com.intellij.java.i18n.JavaI18nBundle;
import com.intellij.lang.properties.psi.Property;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import one.util.streamex.IntStreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;
import org.jetbrains.uast.expressions.UInjectionHost;

import java.util.*;
import java.util.stream.IntStream;

public final class TitleCapitalizationInspection extends AbstractBaseJavaLocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new PsiElementVisitor() {
      @SuppressWarnings("unchecked") 
      private static final Class<? extends UExpression>[] uExpressionClasses = new Class[] 
        {UInjectionHost.class, UCallExpression.class, UReferenceExpression.class};
      
      @Override
      public void visitElement(@NotNull PsiElement element) {
        super.visitElement(element);
        UExpression uElement = UastContextKt.toUElementOfExpectedTypes(element, uExpressionClasses);
        if (uElement == null) return;
        Value titleValue = getTitleValue(uElement, null);
        if (titleValue == null) return;
        List<UExpression> usages = I18nInspection.findIndirectUsages(uElement, false);
        if (usages.isEmpty()) {
          usages = Collections.singletonList(uElement);
        }
        EnumSet<Nls.Capitalization> capitalizationContexts = EnumSet.noneOf(Nls.Capitalization.class);

        for (UExpression usage : usages) {
          capitalizationContexts.add(getCapitalization(usage));
        }
        capitalizationContexts.remove(Nls.Capitalization.NotSpecified);
        for (Nls.Capitalization capitalization : capitalizationContexts) {
          if (!titleValue.isSatisfied(capitalization)) {
            String message;
            LocalQuickFix fix = null;
            if (capitalizationContexts.size() > 1) {
              message = JavaI18nBundle.message("inspection.title.capitalization.mix.description");
            }
            else if (titleValue instanceof DeclaredValue) {
              message = JavaI18nBundle.message("inspection.title.capitalization.mismatch.description",
                                               titleValue, getCapitalizationName(capitalization));
            }
            else {
              fix = titleValue.canFix() &&
                    (element instanceof PsiExpression || uElement instanceof UCallExpression call && getPropertyArgument(call) != null) ? 
                    new TitleCapitalizationFix(titleValue, capitalization) : null;
              message = JavaI18nBundle.message("inspection.title.capitalization.description",
                                               titleValue, getCapitalizationName(capitalization));
            }
            holder.registerProblem(element, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, LocalQuickFix.notNullElements(fix));
          }
        }
      }

      private static Nls.Capitalization getCapitalization(UExpression usage) {
        Nls.Capitalization capitalization = Nls.Capitalization.NotSpecified;
        NlsInfo info = NlsInfo.forExpression(usage, false);
        if (info instanceof NlsInfo.Localized) {
          capitalization = ((NlsInfo.Localized)info).getCapitalization();
        }
        else if (info.getNlsStatus() == ThreeState.UNSURE) {
          UElement parent = usage.getUastParent();
          if (usage instanceof UCallExpression && parent instanceof UQualifiedReferenceExpression) {
            parent = parent.getUastParent();
          }
          UCallExpression call = ObjectUtils.tryCast(parent, UCallExpression.class);
          if (call != null) {
            PsiParameter parameter = AnnotationContext.getParameter(call, usage);
            if (parameter != null) {
              capitalization = getSupplierCapitalization(parameter);
            }
          }
        }
        return capitalization;
      }
    };
  }

  private static @Nls String getCapitalizationName(Nls.Capitalization capitalization) {
    return switch (capitalization) {
      case Title -> JavaI18nBundle.message("capitalization.kind.title");
      case Sentence -> JavaI18nBundle.message("capitalization.kind.sentence");
      default -> throw new IllegalArgumentException();
    };
  }

  private static @Nullable Value getTitleValue(@Nullable UExpression arg, @Nullable Set<? super UExpression> processed) {
    if (arg instanceof UInjectionHost) {
      return Value.of((UInjectionHost)arg);
    }
    if (arg instanceof UCallExpression call) {
      UExpression returnValue = StringFlowUtil.getReturnValue(call);
      if (arg.equals(returnValue)) {
        return null;
      }
      if (returnValue != null) {
        if (processed == null) {
          processed = new HashSet<>();
        }
        if (processed.add(returnValue)) {
          return getTitleValue(returnValue, processed);
        }
      }
      Value fromProperty = Value.of(getPropertyArgument(call), call.getValueArgumentCount() > 1);
      if (fromProperty != null) {
        return fromProperty;
      }
    }
    if (arg instanceof UResolvable) {
      PsiElement target = ((UResolvable)arg).resolve();
      if (target instanceof PsiModifierListOwner) {
        Value value = Value.of(NlsInfo.forModifierListOwner((PsiModifierListOwner)target));
        if (value != null) {
          return value;
        }
      }
      PsiType type = arg.getExpressionType();
      if (type != null) {
        return Value.of(NlsInfo.forType(type));
      }
    }
    return null;
  }

  private static @Nullable Property getPropertyArgument(UCallExpression arg) {
    List<UExpression> args = arg.getValueArguments();
    if (!args.isEmpty()) {
      return JavaI18nUtil.resolveProperty(args.get(0));
    }
    return null;
  }

  private static class TitleCapitalizationFix extends PsiUpdateModCommandQuickFix {
    private final Value myTitleValue;
    private final Nls.Capitalization myCapitalization;

    TitleCapitalizationFix(Value titleValue, Nls.Capitalization capitalization) {
      myTitleValue = titleValue;
      myCapitalization = capitalization;
    }

    @Override
    public @NotNull String getName() {
      return JavaI18nBundle.message("quickfix.text.title.capitalization", myTitleValue);
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement problemElement, @NotNull ModPsiUpdater updater) {
      PsiLiteralExpression literal = updater.getWritable(getTargetLiteral(problemElement));
      if (literal != null) {
        Value value = Value.of(literal);
        if (value == null) return;
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        final PsiExpression newExpression =
          factory.createExpressionFromText('"' + StringUtil.escapeStringCharacters(value.fixCapitalization(myCapitalization)) + '"',
                                           problemElement);
        literal.replace(newExpression);
      }
      UElement uElement = UastContextKt.toUElement(problemElement);
      if (uElement instanceof UQualifiedReferenceExpression ref) {
        uElement = ref.getSelector();
      }
      if (uElement instanceof UCallExpression call) {
        final Property property = updater.getWritable(getPropertyArgument(call));
        Value value = Value.of(property, call.getValueArgumentCount() > 1);
        if (value == null) return;
        property.setValue(value.fixCapitalization(myCapitalization));
      }
    }

    private static @Nullable PsiLiteralExpression getTargetLiteral(@NotNull PsiElement element) {
      if (element instanceof PsiLiteralExpression) {
        return (PsiLiteralExpression)element;
      }
      if (element instanceof PsiMethodCallExpression call) {
        final PsiMethod method = call.resolveMethod();
        final PsiExpression returnValue = PropertyUtilBase.getGetterReturnExpression(method);
        if (returnValue != null) {
          return ObjectUtils.tryCast(returnValue, PsiLiteralExpression.class);
        }
      }
      if (element instanceof PsiReferenceExpression referenceExpression) {
        final PsiVariable variable = ObjectUtils.tryCast(referenceExpression.resolve(), PsiVariable.class);
        if (variable == null) return null;
        if (variable.hasModifierProperty(PsiModifier.FINAL)) {
          return ObjectUtils.tryCast(variable.getInitializer(), PsiLiteralExpression.class);
        }
      }
      return null;
    }

    @Override
    public @NotNull String getFamilyName() {
      return JavaI18nBundle.message("quickfix.family.title.capitalization.fix");
    }
  }

  interface Value {
    @Override
    @NotNull String toString();
    boolean isSatisfied(@NotNull Nls.Capitalization capitalization);

    default @NotNull String fixCapitalization(@NotNull Nls.Capitalization capitalization) {
      return NlsCapitalizationUtil.fixValue(toString(), capitalization);
    }

    default boolean canFix() { return true; }

    @Contract("null, _ -> null")
    static @Nullable Value of(@Nullable Property property, boolean useFormat) {
      if (property == null) return null;
      String value = property.getUnescapedValue();
      if (value == null) return null;
      if (useFormat) {
        try {
          MessagePattern pattern = new MessagePattern(MessagePattern.ApostropheMode.DOUBLE_REQUIRED);
          pattern.parse(value);
          return new PropertyValue(value, pattern);
        }
        catch (IllegalArgumentException ignore) {
        }
      }
      return new TextValue(value);
    }

    static Value of(@NotNull UInjectionHost literal) {
      String value = literal.evaluateToString();
      return value == null ? null : new TextValue(value);
    }

    static @Nullable Value of(@NotNull PsiLiteralExpression literal) {
      Object value = literal.getValue();
      return value instanceof String ? new TextValue((String)value) : null;
    }

    static @Nullable Value of(NlsInfo info) {
      if (info instanceof NlsInfo.Localized) {
        Nls.Capitalization capitalization = ((NlsInfo.Localized)info).getCapitalization();
        if (capitalization != Nls.Capitalization.NotSpecified) {
          return new DeclaredValue(capitalization);
        }
      }
      return null;
    }
  }

  static class TextValue implements Value {
    private final String myText;

    TextValue(String text) { myText = text; }

    @Override
    public @NotNull String toString() { return myText;}

    @Override
    public boolean isSatisfied(@NotNull Nls.Capitalization capitalization) {
      return NlsCapitalizationUtil.isCapitalizationSatisfied(StringUtil.stripHtml(myText, true), capitalization);
    }
  }

  static class DeclaredValue implements Value {
    private final Nls.Capitalization myDeclared;

    DeclaredValue(Nls.Capitalization declared) {
      myDeclared = declared;
    }

    @Override
    public boolean isSatisfied(@NotNull Nls.Capitalization capitalization) {
      return capitalization == Nls.Capitalization.NotSpecified || capitalization == myDeclared;
    }

    @Override
    public boolean canFix() {
      return false;
    }

    @Override
    public @NotNull String toString() { return getCapitalizationName(myDeclared);}
  }

  static class PropertyValue implements Value {
    private final String myPresentation;
    private final MessagePattern myPattern;

    PropertyValue(String presentation, MessagePattern pattern) {
      myPresentation = presentation;
      myPattern = pattern;
    }

    @Override
    public @NotNull String toString() {
      return myPresentation;
    }
    
    private int getMessagesForPart(int index) {
      MessagePattern.Part part = myPattern.getPart(index);
      if (part.getType() != MessagePattern.Part.Type.ARG_START) return 0;
      int limitPart = myPattern.getLimitPartIndex(index);
      int msgCount = 0;
      int nesting = -1;
      for (int i = index + 1; i < limitPart; i++) {
        part = myPattern.getPart(i);
        if (part.getType() == MessagePattern.Part.Type.MSG_START) {
          if (nesting == -1) {
            nesting = part.getValue();
          }
          else if (nesting != part.getValue()) {
            continue;
          }
          msgCount++;
        }
      }
      return msgCount;
    }

    @Override
    public boolean isSatisfied(@NotNull Nls.Capitalization capitalization) {
      if (capitalization == Nls.Capitalization.NotSpecified) return true;
      int parts = myPattern.countParts();
      int maxMsgCount = IntStreamEx.range(parts).map(this::getMessagesForPart).append(1).max().orElse(1);
      String string = myPattern.getPatternString();
      // The idea is to replace ordinary parameters with _ (which is not reported in any case) 
      // and choice/plural with one of the possible options in a loop till maximal option number is reached. 
      // If all the artificial strings satisfy the capitalization, we assume that it's satisfied for the pattern as well.
      for (int curIndex = 0; curIndex < maxMsgCount; curIndex++) {
        StringBuilder sample = new StringBuilder();
        int msgIndex = 0;
        int nestingLevel = 0;
        int curMsgCount = 0;
        boolean inMsg = false;
        for (int i = 1; i < parts; i++) {
          MessagePattern.Part part = myPattern.getPart(i);
          boolean shouldCopyPart = nestingLevel == 0 || inMsg && msgIndex == curIndex % curMsgCount + 1;
          if (shouldCopyPart) {
            sample.append(string, myPattern.getPart(i - 1).getLimit(), myPattern.getPatternIndex(i));
          }
          if (part.getType() == MessagePattern.Part.Type.ARG_START) {
            nestingLevel++;
            MessagePattern.ArgType argType = part.getArgType();
            if ((argType == MessagePattern.ArgType.SIMPLE || argType == MessagePattern.ArgType.NONE) && shouldCopyPart) {
              sample.append("_");
            }
            msgIndex = 0;
            curMsgCount = Math.max(1, getMessagesForPart(i));
          }
          else if (part.getType() == MessagePattern.Part.Type.MSG_START) {
            msgIndex++;
            inMsg = true;
          }
          else if (part.getType() == MessagePattern.Part.Type.MSG_LIMIT) {
            inMsg = false;
          }
          else if (part.getType() == MessagePattern.Part.Type.ARG_LIMIT) {
            nestingLevel--;
          }
        }
        if (!NlsCapitalizationUtil.isCapitalizationSatisfied(sample.toString(), capitalization)) return false;
      }
      return true;
    }

    @Override
    public boolean canFix() {
      return IntStream.range(0, myPattern.countParts()).anyMatch(idx -> {
        MessagePattern.ArgType type = myPattern.getPart(idx).getArgType();
        return type == MessagePattern.ArgType.NONE || type == MessagePattern.ArgType.SIMPLE;
      });
    }
  }

  private static Nls.@NotNull Capitalization getSupplierCapitalization(PsiParameter parameter) {
    PsiClassType classType = ObjectUtils.tryCast(parameter.getType(), PsiClassType.class);
    if (classType != null &&
        classType.equalsToText(CommonClassNames.JAVA_UTIL_FUNCTION_SUPPLIER + "<" + CommonClassNames.JAVA_LANG_STRING + ">")) {
      PsiType typeParameter = ArrayUtil.getFirstElement(classType.getParameters());
      if (typeParameter != null) {
        NlsInfo info = NlsInfo.forType(typeParameter);
        if (info instanceof NlsInfo.Localized) {
          return ((NlsInfo.Localized)info).getCapitalization();
        }
      }
    }
    return Nls.Capitalization.NotSpecified;
  }
}
