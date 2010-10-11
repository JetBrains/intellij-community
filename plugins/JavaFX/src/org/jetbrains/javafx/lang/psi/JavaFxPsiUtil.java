package org.jetbrains.javafx.lang.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.lang.psi.impl.JavaFxQualifiedName;

import java.lang.reflect.Array;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxPsiUtil {

  public static <T extends JavaFxElement> T[] nodesToPsi(ASTNode[] nodes, T[] array) {
    T[] psiElements = (T[])Array.newInstance(array.getClass().getComponentType(), nodes.length);
    for (int i = 0; i < nodes.length; i++) {
      //noinspection unchecked
      psiElements[i] = (T)nodes[i].getPsi();
    }
    return psiElements;
  }

  @NotNull
  public static String getPackageNameForElement(@NotNull final JavaFxElement fxElement) {
    final JavaFxFile file = (JavaFxFile)fxElement.getContainingFile();
    if (file != null) {
      final JavaFxPackageDefinition packageDefinition = file.getPackageDefinition();
      if (packageDefinition != null) {
        final String name = packageDefinition.getName();
        if (name != null) {
          return name;
        }
      }
    }
    return "";
  }

  private JavaFxPsiUtil() {
  }

  @Nullable
  public static JavaFxQualifiedName getQName(final JavaFxElement element) {
    final String name = element.getName();
    if (name == null) {
      return null;
    }
    final String fileName = FileUtil.getNameWithoutExtension(element.getContainingFile().getName());
    final String res = fileName + ((element instanceof JavaFxClassDefinition && fileName.equals(name)) ? "" : "." + name);

    final String packageName = getPackageNameForElement(element);
    if (!"".equals(packageName)) {
      return JavaFxQualifiedName.fromString(packageName + "." + res);
    }
    return JavaFxQualifiedName.fromString(res);
  }
}
