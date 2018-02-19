// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

Binding bind
try {
  bind = scriptBinding
}
catch (MissingPropertyException ignore) {
  bind = scriptBinding = new Binding()
}
try {
  def result = new GroovyShell(bind).run(((String)line).replaceAll('###\\\\n', '\n'), 'ideaGroovyConsole.groovy')
  System.out.println()
  if (result != null) {
    System.out.println 'ee2d5778-e2f4-4705-84ef-0847535c32f4' + result
  }
}
catch (Throwable e) {
  e.printStackTrace()
}
