package org.jetbrains.android.augment;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiClass;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.dom.manifest.Permission;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
* @author Eugene.Kudelevsky
*/
class PermissionClass extends ManifestInnerClass {
  PermissionClass(@NotNull AndroidFacet facet, @NotNull PsiClass context) {
    super(facet, "permission", context);
  }

  @NotNull
  @Override
  protected List<Pair<String, String>> doGetFields(@NotNull Manifest manifest) {
    final List<Pair<String, String>> result = new ArrayList<Pair<String, String>>();

    for (Permission permission : manifest.getPermissions()) {
      final String name = permission.getName().getValue();

      if (name != null && name.length() > 0) {
        final int lastDotIndex = name.lastIndexOf('.');
        final String lastId = name.substring(lastDotIndex + 1);

        if (lastId.length() > 0) {
          result.add(Pair.create(AndroidResourceUtil.getFieldNameByResourceName(lastId), name));
        }
      }
    }
    return result;
  }
}
