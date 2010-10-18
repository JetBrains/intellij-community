package org.jetbrains.android.dom.layout;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.dom.AndroidResourceDomFileDescription;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class LayoutDomFileDescription extends AndroidResourceDomFileDescription<LayoutViewElement> {
  public LayoutDomFileDescription() {
    super(LayoutViewElement.class, "view", "layout");
  }

  public boolean acceptsOtherRootTagNames() {
    return true;
  }

  public static boolean isLayoutFile(@NotNull final XmlFile file) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        return new LayoutDomFileDescription().isMyFile(file, null);
      }
    });
  }
}
