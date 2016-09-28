/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.intellij.application.options.PathMacrosImpl
import com.intellij.openapi.components.impl.BasePathMacroManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SystemProperties
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.jps.model.serialization.PathMacroUtil
import org.junit.Test

class LightPathMacroManagerTest {
  @Test
  fun systemOverridesUser() {
    val macros = PathMacrosImpl()
    val userHome = FileUtil.toSystemIndependentName(SystemProperties.getUserHome())
    macros.setMacro("foo", userHome)

    val manager = BasePathMacroManager(macros)
    assertThat(manager.collapsePath(userHome)).isEqualTo("$${PathMacroUtil.USER_HOME_NAME}$")
  }
}