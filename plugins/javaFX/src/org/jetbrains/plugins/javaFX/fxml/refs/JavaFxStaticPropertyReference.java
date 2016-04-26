package org.jetbrains.plugins.javaFX.fxml.refs;

import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

/**
 * @author Pavel.Dolgov
 */
public class JavaFxStaticPropertyReference extends PsiReferenceBase<XmlAttribute> {
  private String myPropertyName;
  private PsiMethod myStaticMethod;

  public JavaFxStaticPropertyReference(@NotNull XmlAttribute xmlAttribute,
                                       @Nullable PsiClass psiClass,
                                       @NotNull String propertyName) {
    super(xmlAttribute);
    myPropertyName = propertyName;
    myStaticMethod = JavaFxPsiUtil.findStaticPropertySetter(propertyName, psiClass);
  }

  @Nullable
  @Override
  public PsiElement resolve() {
    return myStaticMethod;
  }

  public PsiType getType() {
    if (myStaticMethod != null) {
      final PsiParameter[] parameters = myStaticMethod.getParameterList().getParameters();
      if (parameters.length == 2) {
        return parameters[1].getType();
      }
    }
    return null;
  }

  public String getPropertyName() {
    return myPropertyName;
  }

  public PsiMethod getStaticMethod() {
    return myStaticMethod;
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final String newPropertyName = JavaFxPsiUtil.getPropertyName(newElementName, true);
    return super.handleElementRename(newPropertyName);
  }
}
