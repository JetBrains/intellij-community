package org.jetbrains.plugins.emojipicker.messages;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;
import org.jetbrains.plugins.emojipicker.EmojiCategory;

import java.util.function.Supplier;

public class EmojiCategoriesBundle extends DynamicBundle {
  @NonNls private static final String BUNDLE = "messages.EmojiCategoriesBundle";
  private static final EmojiCategoriesBundle INSTANCE = new EmojiCategoriesBundle();

  private EmojiCategoriesBundle() {
    super(BUNDLE);
  }

  @NotNull
  public static @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }

  @NotNull
  public static Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
                                                     Object @NotNull ... params) {
    return INSTANCE.getLazyMessage(key, params);
  }

  public static @Nls String findNameForCategory(EmojiCategory category) {
    if (category == null) return null;
    return message("category.EmojiPicker." + category.getId());
  }
}
