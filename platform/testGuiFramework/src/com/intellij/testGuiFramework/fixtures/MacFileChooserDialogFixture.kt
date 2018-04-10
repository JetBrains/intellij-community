/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.testGuiFramework.fixtures

import org.fest.swing.core.Robot
import java.util.concurrent.TimeUnit
import javax.script.ScriptEngineManager




class MacFileChooserDialogFixture(val robot: Robot) {

  fun selectByPath(path: String) {
    val script = createAppleScript(path)
    performAppleScriptThroughCmd(script)
  }

  private fun performAppleScript(script: String) {
    val mgr = ScriptEngineManager()
    val engine = mgr.getEngineByName("AppleScript")
    engine.eval(script)
  }

  private fun performAppleScriptThroughCmd(script: String) {
    val processBuilder = ProcessBuilder(arrayListOf("Terminal.app", "osascript", "-e", script))
    processBuilder.redirectErrorStream(true)
    val process = processBuilder.start()
    if (!process.waitFor(30, TimeUnit.SECONDS)) {
      throw Exception("Apple script process exceeded the time (30 seconds): \n $script")
    }
  }

  private fun createAppleScript(path: String): String {
    return """tell application "System Events"
	tell process "java"
		repeat until (exists sheet 1 of window 1)
			delay 1
		end repeat
    delay 5
		keystroke "G" using {command down, shift down}
		repeat until (exists sheet 1 of sheet 1 of window 1)
			delay 1
		end repeat
		delay 1
		keystroke "$path"
		click ((buttons of sheet 1 of sheet 1 of window 1) whose title is "Go")
		delay 5
		repeat until (not (exists sheets of sheet 1 of window 1))
			delay 1
		end repeat
		click ((buttons of sheet 1 of window 1) whose title is "Open")
		repeat until (not (exists sheet 1 of window 1))
			delay 1
		end repeat
	end tell
end tell"""}

}