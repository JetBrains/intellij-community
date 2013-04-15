package org.jetbrains.android.dom.layout;

import com.intellij.openapi.module.Module;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class FragmentLayoutDomFileDescription extends LayoutDomFileDescription<Fragment> {
  public static final String FRAGMENT_TAG_NAME = "fragment";

  public FragmentLayoutDomFileDescription() {
    super(Fragment.class, FRAGMENT_TAG_NAME);
  }

  @Override
  public boolean isMyFile(@NotNull XmlFile file, @Nullable Module module) {
    return super.isMyFile(file, module) && hasFragmentRootTag(file);
  }

  static boolean hasFragmentRootTag(@NotNull XmlFile file) {
    final XmlTag rootTag = file.getRootTag();
    return rootTag != null && FRAGMENT_TAG_NAME.equals(rootTag.getName());
  }
}

