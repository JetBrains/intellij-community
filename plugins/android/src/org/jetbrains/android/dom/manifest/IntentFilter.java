package org.jetbrains.android.dom.manifest;

import java.util.List;

/**
 * @author yole
 */
public interface IntentFilter extends ManifestElement {
    List<Action> getActions();
    List<Category> getCategories();

    Action addAction();
    Category addCategory();
}
