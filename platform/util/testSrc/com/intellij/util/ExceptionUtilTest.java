// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.util.containers.ContainerUtil;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class ExceptionUtilTest {

  @Test
  public void findCauseAndSuppressed() {
    Throwable exc = new RuntimeException("exc", new RuntimeException("exc-cause", new IllegalStateException("exc-cause-cause")));
    exc.addSuppressed(new RuntimeException("exc-suppressed"));
    exc.getCause().addSuppressed(new IllegalStateException("exc-cause-suppressed"));

    Assertions
      .assertThat(ContainerUtil.map(ExceptionUtil.findCauseAndSuppressed(exc, Throwable.class), Throwable::getMessage))
      .containsExactly("exc", "exc-cause", "exc-cause-cause",  "exc-suppressed", "exc-cause-suppressed");
    Assertions
      .assertThat(ContainerUtil.map(ExceptionUtil.findCauseAndSuppressed(exc, IllegalStateException.class), Throwable::getMessage))
      .containsExactly("exc-cause-cause", "exc-cause-suppressed");
  }

  @Test
  public void findCauseAndSuppressedCircularReferences() {
    RuntimeException cause = new RuntimeException("exc-cause", new IllegalStateException("exc-cause-cause"));
    Throwable exc = new RuntimeException("exc", cause);
    cause.addSuppressed(exc);

    exc.addSuppressed(new RuntimeException("exc-suppressed"));
    IllegalStateException suppressed = new IllegalStateException("exc-cause-suppressed");
    exc.getCause().addSuppressed(suppressed);
    suppressed.addSuppressed(exc);

    Assertions
      .assertThat(ContainerUtil.map(ExceptionUtil.findCauseAndSuppressed(exc, Throwable.class), Throwable::getMessage))
      .containsExactly("exc", "exc-cause", "exc-cause-cause",  "exc-suppressed", "exc-cause-suppressed");
    Assertions
      .assertThat(ContainerUtil.map(ExceptionUtil.findCauseAndSuppressed(exc, IllegalStateException.class), Throwable::getMessage))
      .containsExactly("exc-cause-cause", "exc-cause-suppressed");
  }
}