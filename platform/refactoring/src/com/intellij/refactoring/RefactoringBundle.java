// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring;

import com.intellij.DynamicBundle;
import com.intellij.ide.IdeDeprecatedMessagesBundle;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.*;

import java.util.function.Supplier;

public final class RefactoringBundle extends DynamicBundle {
  @NonNls private static final String BUNDLE = "messages.RefactoringBundle";
  private static final RefactoringBundle INSTANCE = new RefactoringBundle();

  private RefactoringBundle() { super(BUNDLE); }

  @NotNull
  public static @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    if (INSTANCE.containsKey(key)) {
      return INSTANCE.getMessage(key, params);
    }
    return IdeDeprecatedMessagesBundle.message(key, params);
  }

  @NotNull
  public static Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    if (INSTANCE.containsKey(key)) {
      return INSTANCE.getLazyMessage(key, params);
    }
    return IdeDeprecatedMessagesBundle.messagePointer(key, params);
  }

  @NotNull
  public static @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key) {
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

  public static @NlsContexts.DialogMessage String getCannotRefactorMessage(@NlsContexts.DialogMessage @Nullable final String message) {
    return message("cannot.perform.refactoring") + (message == null ? "" : "\n" + message);
  }
}