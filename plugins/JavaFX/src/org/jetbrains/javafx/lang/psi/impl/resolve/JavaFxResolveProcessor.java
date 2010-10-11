package org.jetbrains.javafx.lang.psi.impl.resolve;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.psi.JavaFxFile;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxResolveProcessor implements PsiScopeProcessor {
  private final String myName;
  private PsiElement myResult;

  public JavaFxResolveProcessor(final String name) {
    myName = (name != null && StringUtil.startsWith(name, "<<")) ? getRealName(name) : name;
  }

  @Override
  public boolean execute(final PsiElement element, final ResolveState state) {
    if (myResult != null) {
      return false;
    }
    if (element instanceof PsiNamedElement) {
      String elementName = ((PsiNamedElement)element).getName();
      if (elementName != null) {
        if (element instanceof JavaFxFile) {
          elementName = FileUtil.getNameWithoutExtension(elementName);
        }
        if (isNamesEqual(elementName)) {
          myResult = element;
          return false;
        }
      }
      // compiled code
      if (element instanceof PsiModifierListOwner) {
        final PsiModifierList modifierList = ((PsiModifierListOwner)element).getModifierList();
        if (modifierList != null) {
          final PsiAnnotation annotation = modifierList.findAnnotation("com.sun.javafx.runtime.annotation.SourceName");
          if (annotation != null) {
            final PsiAnnotationMemberValue value = annotation.findAttributeValue("value");
            if (value instanceof PsiLiteralExpression) {
              final Object o = ((PsiLiteralExpression)value).getValue();
              if (o instanceof String) {
                if (isNamesEqual((String)o)) {
                  myResult = element;
                  return false;
                }
              }
            }
          }
        }
      }
    }
    return true;
  }

  private boolean isNamesEqual(@NotNull final String elementName) {
    if (elementName.equals(myName)) {
      return true;
    }
    if (StringUtil.startsWith(elementName, "<<")) {
      return getRealName(elementName).equals(myName);
    }
    return false;
  }

  @NotNull
  private static String getRealName(final String elementName) {
    return elementName.substring(2, elementName.length() - 2);
  }

  @Override
  public <T> T getHint(final Key<T> hintKey) {
    return null;
  }

  public String getName() {
    return myName;
  }

  @Override
  public void handleEvent(final Event event, final Object associated) {
  }

  public PsiElement getResult() {
    return myResult;
  }
}
