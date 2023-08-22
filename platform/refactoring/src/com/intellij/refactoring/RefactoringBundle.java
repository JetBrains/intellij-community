// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring;

import com.intellij.DynamicBundle;
import com.intellij.ide.IdeDeprecatedMessagesBundle;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.*;

import java.util.function.Supplier;

public final class RefactoringBundle {
  private static final @NonNls String BUNDLE = "messages.RefactoringBundle";
  private static final DynamicBundle INSTANCE = new DynamicBundle(RefactoringBundle.class, BUNDLE);

  private RefactoringBundle() {}

  public static @NotNull @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    if (INSTANCE.containsKey(key)) {
      return INSTANCE.getMessage(key, params);
    }
    return IdeDeprecatedMessagesBundle.message(key, params);
  }

  public static @NotNull Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    if (INSTANCE.containsKey(key)) {
      return INSTANCE.getLazyMessage(key, params);
    }
    return IdeDeprecatedMessagesBundle.messagePointer(key, params);
  }

  public static @NotNull @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key) {
    if (INSTANCE.containsKey(key)) {
      return INSTANCE.getMessage(key);
    }
    return IdeDeprecatedMessagesBundle.message(key);
  }

  public static @NlsContexts.Label String getSearchInCommentsAndStringsText() {
    return message("search.in.comments.and.strings");
  }

  public static @NlsContexts.Label String getSearchForTextOccurrencesText() {
    return message("search.for.text.occurrences");
  }

  public static @Nls String getVisibilityPackageLocal() {
    return message("visibility.package.local");
  }

  public static @Nls String getVisibilityPrivate() {
    return message("visibility.private");
  }

  public static @Nls String getVisibilityProtected() {
    return message("visibility.protected");
  }

  public static @Nls String getVisibilityPublic() {
    return message("visibility.public");
  }

  public static @Nls String getVisibilityAsIs() {
    return message("visibility.as.is");
  }

  public static @Nls String getEscalateVisibility() {
    return message("visibility.escalate");
  }

  public static @NlsContexts.DialogMessage String getCannotRefactorMessage(final @NlsContexts.DialogMessage @Nullable String message) {
    return message("cannot.perform.refactoring") + (message == null ? "" : "\n" + message);
  }
}
