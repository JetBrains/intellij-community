package org.jetbrains.plugins.javaFX.fxml.codeInsight;

import com.intellij.codeInsight.daemon.impl.JavaColorProvider;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonNames;

import java.awt.*;
import java.text.DecimalFormat;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.IntFunction;

/**
 * @author Pavel.Dolgov
 */
public class JavaFxColorProvider extends JavaColorProvider {
  private static final String COLOR = "color";
  private static final String RGB = "rgb";
  private static final String GRAY = "gray";
  private static final String GRAY_RGB = "grayRgb";
  private static final String HSB = "hsb";
  private static final Set<String> FACTORY_METHODS = ContainerUtil.immutableSet(COLOR, RGB, GRAY, GRAY_RGB, HSB);

  @Override
  public Color getColorFrom(@NotNull PsiElement element) {
    if (element instanceof PsiNewExpression) {
      PsiNewExpression newExpression = (PsiNewExpression)element;
      if (isColorClass(PsiTypesUtil.getPsiClass(newExpression.getType()))) {
        PsiExpressionList argumentList = newExpression.getArgumentList();
        if (argumentList != null) {
          PsiExpression[] args = argumentList.getExpressions();
          if (args.length == 4) {
            Object[] values = getArgumentValues(args);
            return getScaledRgbColor(values[0], values[1], values[2], values[3]);
          }
        }
      }
    }
    if (element instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression methodCall = (PsiMethodCallExpression)element;
      PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
      String methodName = methodExpression.getReferenceName();
      if (FACTORY_METHODS.contains(methodName)) {
        PsiElement resolved = methodExpression.resolve();
        if (resolved instanceof PsiMethod) {
          PsiMethod method = (PsiMethod)resolved;
          if (method.hasModifierProperty(PsiModifier.STATIC)) {
            if (isColorClass(method.getContainingClass())) {
              return getColor(methodName, methodCall.getArgumentList());
            }
          }
        }
      }
    }
    return null;
  }

  private static boolean isColorClass(@Nullable PsiClass aClass) {
    return aClass != null && JavaFxCommonNames.JAVAFX_SCENE_COLOR.equals(aClass.getQualifiedName());
  }

  @Nullable
  private static Color getColor(@Nullable String methodName, @NotNull PsiExpressionList argumentList) {
    Object[] values = getArgumentValues(argumentList.getExpressions());
    if (COLOR.equals(methodName)) {
      switch (values.length) {
        case 4: return getScaledRgbColor(values[0], values[1], values[2], values[3]);
        case 3: return getScaledRgbColor(values[0], values[1], values[2], Double.valueOf(1));
      }
    }
    else if (RGB.equals(methodName)) {
      switch (values.length) {
        case 4: return getRgbColor(values[0], values[1], values[2], values[3]);
        case 3: return getRgbColor(values[0], values[1], values[2], Double.valueOf(1));
      }
    }
    else if (GRAY.equals(methodName)) {
      switch (values.length) {
        case 2: return getScaledRgbColor(values[0], values[0], values[0], values[1]);
        case 1: return getScaledRgbColor(values[0], values[0], values[0], Double.valueOf(1));
      }
    }
    else if (GRAY_RGB.equals(methodName)) {
      switch (values.length) {
        case 2: return getRgbColor(values[0], values[0], values[0], values[1]);
        case 1: return getRgbColor(values[0], values[0], values[0], Double.valueOf(1));
      }
    }
    else if (HSB.equals(methodName)) {
      switch (values.length) {
        case 4: return getHsbColor(values[0], values[1], values[2], values[3]);
        case 3: return getHsbColor(values[0], values[1], values[2], Double.valueOf(1));
      }
    }
    return null;
  }

  @NotNull
  private static Object[] getArgumentValues(@NotNull PsiExpression[] argumentExpressions) {
    return ContainerUtil.map(argumentExpressions,
                             expression -> JavaConstantExpressionEvaluator.computeConstantExpression(expression, true),
                             ArrayUtil.EMPTY_OBJECT_ARRAY);
  }

  @Nullable
  private static Color getScaledRgbColor(@Nullable Object redValue,
                                         @Nullable Object greenValue,
                                         @Nullable Object blueValue,
                                         @Nullable Object alphaValue) {
    Integer red = getScaledComponent(redValue);
    Integer green = getScaledComponent(greenValue);
    Integer blue = getScaledComponent(blueValue);
    Integer alpha = getScaledComponent(alphaValue);
    if (red != null && green != null && blue != null && alpha != null) {
      //noinspection UseJBColor
      return new Color(red, green, blue, alpha);
    }
    return null;
  }

  @Nullable
  private static Color getRgbColor(@Nullable Object redValue,
                                   @Nullable Object greenValue,
                                   @Nullable Object blueValue,
                                   @Nullable Object alphaValue) {
    Integer red = getComponent(redValue);
    Integer green = getComponent(greenValue);
    Integer blue = getComponent(blueValue);
    Integer alpha = getScaledComponent(alphaValue);
    if (red != null && green != null && blue != null && alpha != null) {
      //noinspection UseJBColor
      return new Color(red, green, blue, alpha);
    }
    return null;
  }

  private static Integer getComponent(Object value) {
    if (value instanceof Number) {
      return ((Number)value).intValue();
    }
    return null;
  }

  private static Integer getScaledComponent(Object value) {
    if (value instanceof Number) {
      double doubleValue = ((Number)value).doubleValue();
      return (int)(doubleValue * 255 + 0.5);
    }
    return null;
  }

  private static Float getHsbComponent(Object value) {
    if (value instanceof Number) {
      return ((Number)value).floatValue();
    }
    return null;
  }

  @Override
  public void setColorTo(@NotNull PsiElement element, @NotNull Color color) {
    Runnable command = null;
    if (element instanceof PsiNewExpression) {
      final PsiNewExpression expr = (PsiNewExpression)element;
      PsiExpressionList argumentList = expr.getArgumentList();
      assert argumentList != null;
      command = () -> replaceConstructorArgs(color, argumentList);
    }
    if (element instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression methodCall = (PsiMethodCallExpression)element;
      PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
      String methodName = methodExpression.getReferenceName();
      if (COLOR.equals(methodName) || GRAY.equals(methodName)) {
        command = () -> replaceColor(methodCall, getScaledRgbCallText(color));
      }
      else if (RGB.equals(methodName) || GRAY_RGB.equals(methodName)) {
        command = () -> replaceColor(methodCall, getRgbCallText(color));
      }
      else if (HSB.equals(methodName)) {
        command = () -> replaceColor(methodCall, getHsbCallText(color));
      }
    }

    if (command != null) {
      Document document = PsiDocumentManager.getInstance(element.getProject()).getDocument(element.getContainingFile());
      CommandProcessor.getInstance()
        .executeCommand(element.getProject(), command, IdeBundle.message("change.color.command.text"), null, document);
    }
  }

  private static void replaceConstructorArgs(@NotNull Color color, PsiExpressionList argumentList) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(argumentList.getProject());
    String text = JavaFxCommonNames.JAVAFX_SCENE_COLOR + "(" +
                  formatScaledComponent(color.getRed()) + "," +
                  formatScaledComponent(color.getGreen()) + "," +
                  formatScaledComponent(color.getBlue()) + "," +
                  formatScaledComponent(color.getAlpha()) + ")";
    PsiMethodCallExpression newCall = (PsiMethodCallExpression)factory.createExpressionFromText(text, argumentList);
    argumentList.replace(newCall.getArgumentList());
  }

  private static void replaceColor(@NotNull PsiMethodCallExpression methodCall, @NotNull String callText) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(methodCall.getProject());
    PsiMethodCallExpression newCall = (PsiMethodCallExpression)factory.createExpressionFromText(callText, methodCall);
    methodCall.getArgumentList().replace(newCall.getArgumentList());

    PsiElement nameElement = methodCall.getMethodExpression().getReferenceNameElement();
    assert nameElement != null;
    PsiElement newNameElement = newCall.getMethodExpression().getReferenceNameElement();
    assert newNameElement != null;
    nameElement.replace(newNameElement);
  }

  private static Color getHsbColor(Object hValue, Object sValue, Object bValue, Object alphaValue) {
    Float h = getHsbComponent(hValue);
    Float s = getHsbComponent(sValue);
    Float b = getHsbComponent(bValue);
    Integer alpha = getScaledComponent(alphaValue);
    if (h != null && s != null && b != null && alpha != null) {
      Color hsbColor = Color.getHSBColor(h / 360.0f, s, b);
      //noinspection UseJBColor
      return alpha == 255 ? hsbColor : new Color(hsbColor.getRed(), hsbColor.getGreen(), hsbColor.getBlue(), alpha);
    }
    return null;
  }

  @NotNull
  private static String getScaledRgbCallText(@NotNull Color color) {
    return getCallText(color, COLOR, GRAY, JavaFxColorProvider::formatScaledComponent);
  }

  @NotNull
  private static String getRgbCallText(@NotNull Color color) {
    return getCallText(color, RGB, GRAY_RGB, String::valueOf);
  }

  @NotNull
  private static String getCallText(@NotNull Color color, String colorMethodName, String grayMethodName, IntFunction<String> formatter) {
    String methodName;
    StringJoiner args = new StringJoiner(",", "(", ")");
    if (color.getRed() == color.getGreen() && color.getRed() == color.getBlue()) {
      methodName = grayMethodName;
      args.add(formatter.apply(color.getRed()));
    }
    else {
      methodName = colorMethodName;
      args.add(formatter.apply(color.getRed()));
      args.add(formatter.apply(color.getGreen()));
      args.add(formatter.apply(color.getBlue()));
    }
    if (color.getAlpha() != 255) {
      args.add(formatScaledComponent(color.getAlpha()));
    }
    return methodName + args;
  }

  @NotNull
  private static String formatScaledComponent(int colorComponent) {
    DecimalFormat df = new DecimalFormat("#.####"); // not thread safe - can't have a constant
    return df.format(colorComponent / 255.0);
  }

  private static String getHsbCallText(Color color) {
    float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
    DecimalFormat df = new DecimalFormat("#.####");
    StringJoiner args = new StringJoiner(",", "(", ")");
    args.add(df.format(hsb[0] * 360));
    args.add(df.format(hsb[1]));
    args.add(df.format(hsb[2]));
    if (color.getAlpha() != 255) {
      args.add(formatScaledComponent(color.getAlpha()));
    }
    return HSB + args;
  }
}
