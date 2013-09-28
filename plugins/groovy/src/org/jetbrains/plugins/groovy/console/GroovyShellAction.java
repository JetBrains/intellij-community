/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.console;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;

/**
 * @author peter
 */
public class GroovyShellAction extends GroovyShellActionBase {
  protected GroovyShellRunner getRunner(Module module) {
    return new DefaultGroovyShellRunner();
  }

  @Override
  public String getTitle() {
    return "Groovy Shell";
  }

  @Override
  protected GroovyShellConsoleImpl createConsole(Project project, String title) {
    final GroovyShellConsoleImpl console = new GroovyShellConsoleImpl(project, title);

    /*UiNotifyConnector.doWhenFirstShown(console.getComponent(), new Runnable() {
      @Override
      public void run() {
        final String key = "groovy.shell.is.really.groovy.shell";
        if (!PropertiesComponent.getInstance().isTrueValue(key)) {
          final Alarm alarm = new Alarm();
          alarm.addRequest(new Runnable() {
            @Override
            public void run() {
              GotItMessage.createMessage("Groovy Shell & Groovy Console", "<html><div align='left'>Use 'Groovy Console' action (Tools | Groovy Console...) to run <a href='http://'>Groovy Console</a><br>Use 'Groovy Shell' action (Tools | Groovy Shell...) to invoke <a href=\"http://groovy.codehaus.org/Groovy+Shell\">Groovy Shell</a></div></html>")
                .setDisposable(console)
                .show(new RelativePoint(console.getComponent(), new Point(10, 0)), Balloon.Position.above);

              PropertiesComponent.getInstance().setValue(key, String.valueOf(true));
              Disposer.dispose(alarm);
            }
          }, 2000);
        }

      }
    })*/;


    return console;
  }
}
