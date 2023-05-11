package org.jetbrains.plugins.emojipicker.messages;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;
import org.jetbrains.plugins.emojipicker.EmojiCategory;

import java.util.function.Supplier;

public final class EmojiCategoriesBundle {
  private static final @NonNls String BUNDLE = "messages.EmojiCategoriesBundle";
  private static final DynamicBundle INSTANCE = new DynamicBundle(EmojiCategoriesBundle.class, BUNDLE);

  private EmojiCategoriesBundle() {
  }

  public static @NotNull @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }

  public static @NotNull Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
                                                              Object @NotNull ... params) {
    return INSTANCE.getLazyMessage(key, params);
  }

  public static @Nls String findNameForCategory(EmojiCategory category) {
    if (category == null) return null;
    return message("category.EmojiPicker." + category.getId());
  }
}
