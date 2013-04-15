package org.jetbrains.android.dom.layout;

import org.jetbrains.android.dom.AndroidDomExtender;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidLayoutUtil {
  private AndroidLayoutUtil() {
  }

  @NotNull
  public static List<String> getPossibleRoots(@NotNull AndroidFacet facet) {
    final List<String> result = new ArrayList<String>();
    result.add("view");
    result.add("merge");
    result.add(FragmentLayoutDomFileDescription.FRAGMENT_TAG_NAME);
    result.addAll(AndroidDomUtil.removeUnambigiousNames(
      AndroidDomExtender.getViewClassMap(facet)));
    result.remove("View");
    return result;
  }
}
