// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.merge.dialog

import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

class OptionInfo<T>(val option: T,
                    @NonNls val flag: String,
                    @Nls val description: String)