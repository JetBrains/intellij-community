// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui

import org.assertj.swing.core.Robot
import java.awt.Container

interface IftTestContainerFixture<C : Container> {
  fun target(): C
  fun robot(): Robot
}