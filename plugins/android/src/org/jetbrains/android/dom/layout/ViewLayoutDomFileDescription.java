package org.jetbrains.android.dom.layout;

import com.intellij.openapi.module.Module;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class ViewLayoutDomFileDescription extends LayoutDomFileDescription<LayoutViewElement> {
  public ViewLayoutDomFileDescription() {
    super(LayoutViewElement.class, "view");
  }

  @Override
  public boolean isMyFile(@NotNull XmlFile file, @Nullable Module module) {
    return super.isMyFile(file, module) && !FragmentLayoutDomFileDescription.hasFragmentRootTag(file);
  }
}

