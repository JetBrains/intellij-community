// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.scale;

import com.intellij.ui.scale.ScaleContext;
import com.intellij.ui.scale.ScaleType;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.function.Consumer;

import static com.intellij.ui.scale.ScaleType.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests {@link com.intellij.ui.scale.ScaleContext}.
 *
 * Note, mostly {@code ScaleContext} is tested in the icons tests.
 *
 * @author tav
 */
public class ScaleContextTest {
  @Test
  public void testOverrideScale() {
    final ScaleContext origContext = ScaleContext.create();

    Consumer<ScaleType> test = type -> {
      double overriddenScale = origContext.getScale(type) + 1;
      origContext.overrideScale(type.of(overriddenScale));

      // Test (A) 'setScale'

      origContext.setScale(type.of(overriddenScale + 1));

      assertThat(origContext.getScale(type)).
        describedAs("overridden scale is not preserved on 'ScaleContext.setScale': " + type).
        isEqualTo(overriddenScale);

      // Test (B) 'copy'

      ScaleContext contextCopy = origContext.copy();

      assertThat(contextCopy).isNotSameAs(origContext);
      assertContext(origContext, contextCopy);

      contextCopy.setScale(type.of(overriddenScale + 1));

      assertThat(contextCopy.getScale(type)).
        describedAs("overridden scale is not preserved on 'ScaleContext.copy': " + type).
        isEqualTo(overriddenScale);
    };

    test.accept(USR_SCALE);
    test.accept(SYS_SCALE);
    test.accept(OBJ_SCALE);
  }

  private static void assertContext(@NotNull ScaleContext context1, @NotNull ScaleContext context2) {
    Consumer<ScaleType> test = type -> {
      assertThat(context1.getScale(type)).
        describedAs("the scale type: " + type).
        isEqualTo(context2.getScale(type));
    };
    test.accept(USR_SCALE);
    test.accept(SYS_SCALE);
    test.accept(OBJ_SCALE);
  }
}
