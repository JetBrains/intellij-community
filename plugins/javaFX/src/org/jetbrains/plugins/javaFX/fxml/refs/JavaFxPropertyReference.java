package org.jetbrains.plugins.javaFX.fxml.refs;

import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonNames;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

import java.util.Map;

/**
 * @author Pavel.Dolgov
 */
public interface JavaFxPropertyReference extends PsiReference {
  default boolean isRenameable() {
    return resolve() != null;
  }

  @NotNull
  default Map<PsiElement, String> getElementsToRename(@NotNull String newPropertyName) {
    final Map<PsiElement, String> rename = new THashMap<>();
    final PsiField field = getField();
    if (field != null) {
      rename.put(field, newPropertyName);
    }
    final PsiMethod getter = getGetter();
    if (getter != null) {
      rename.put(getter, PropertyUtil.suggestGetterName(newPropertyName, getter.getReturnType()));
    }
    final PsiMethod setter = getSetter();
    if (setter != null) {
      rename.put(setter, PropertyUtil.suggestSetterName(newPropertyName));
    }
    final PsiMethod observableGetter = getObservableGetter();
    if (observableGetter != null) {
      rename.put(observableGetter, newPropertyName + JavaFxCommonNames.PROPERTY_METHOD_SUFFIX);
    }
    //TODO add "name" parameter of the observable property constructor (like new SimpleObjectProperty(this, "name", null);
    return rename;
  }

  @Nullable
  PsiMethod getGetter();

  @Nullable
  PsiMethod getSetter();

  @Nullable
  PsiField getField();

  @Nullable
  PsiMethod getObservableGetter();

  @Nullable
  PsiType getType();

  static PsiMethod getGetter(PsiClass psiClass, String propertyName) {
    if (psiClass == null || propertyName == null) return null;
    return JavaFxPsiUtil.findPropertyGetter(psiClass, propertyName);
  }

  static PsiMethod getSetter(PsiClass psiClass, String propertyName) {
    if (psiClass == null || propertyName == null) return null;
    return JavaFxPsiUtil.findInstancePropertySetter(psiClass, propertyName);
  }

  static PsiField getField(PsiClass psiClass, String propertyName) {
    if (psiClass == null || propertyName == null) return null;
    return psiClass.findFieldByName(propertyName, true);
  }

  static PsiMethod getObservableGetter(PsiClass psiClass, String propertyName) {
    if (psiClass == null || propertyName == null) return null;
    return JavaFxPsiUtil.findObservablePropertyGetter(psiClass, propertyName);
  }
}
