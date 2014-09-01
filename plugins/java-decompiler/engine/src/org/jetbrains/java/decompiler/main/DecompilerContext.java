/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.java.decompiler.main;

import org.jetbrains.java.decompiler.main.collectors.CounterContainer;
import org.jetbrains.java.decompiler.main.collectors.ImportCollector;
import org.jetbrains.java.decompiler.main.collectors.VarNamesCollector;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.modules.renamer.PoolInterceptor;
import org.jetbrains.java.decompiler.struct.StructContext;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;


public class DecompilerContext {

  public static final String CURRENT_CLASS = "CURRENT_CLASS";
  public static final String CURRENT_METHOD = "CURRENT_METHOD";
  public static final String CURRENT_METHOD_DESCRIPTOR = "CURRENT_METHOD_DESCRIPTOR";
  public static final String CURRENT_VAR_PROCESSOR = "CURRENT_VAR_PROCESSOR";

  public static final String CURRENT_CLASSNODE = "CURRENT_CLASSNODE";
  public static final String CURRENT_METHOD_WRAPPER = "CURRENT_METHOD_WRAPPER";

  private static ThreadLocal<DecompilerContext> currentContext = new ThreadLocal<DecompilerContext>();

  private final Map<String, Object> properties;

  private StructContext structcontext;

  private ImportCollector impcollector;

  private VarNamesCollector varncollector;

  private CounterContainer countercontainer;

  private ClassesProcessor classprocessor;

  private PoolInterceptor poolinterceptor;

  private IFernflowerLogger logger;

  private DecompilerContext(Map<String, Object> properties) {
    this.properties = properties;
  }

  public static void initContext(Map<String, Object> propertiesCustom) {
    Map<String, Object> properties = new HashMap<String, Object>(IFernflowerPreferences.DEFAULTS);
    if (propertiesCustom != null) {
      properties.putAll(propertiesCustom);
    }
    currentContext.set(new DecompilerContext(properties));
  }

  public static DecompilerContext getCurrentContext() {
    return currentContext.get();
  }

  public static void setCurrentContext(DecompilerContext context) {
    currentContext.set(context);
  }

  public static Object getProperty(String key) {
    return getCurrentContext().properties.get(key);
  }

  public static void setProperty(String key, Object value) {
    getCurrentContext().properties.put(key, value);
  }

  public static boolean getOption(String key) {
    return "1".equals(getCurrentContext().properties.get(key));
  }

  public static ImportCollector getImpcollector() {
    return getCurrentContext().impcollector;
  }

  public static void setImpcollector(ImportCollector impcollector) {
    getCurrentContext().impcollector = impcollector;
  }

  public static VarNamesCollector getVarncollector() {
    return getCurrentContext().varncollector;
  }

  public static void setVarncollector(VarNamesCollector varncollector) {
    getCurrentContext().varncollector = varncollector;
  }

  public static StructContext getStructcontext() {
    return getCurrentContext().structcontext;
  }

  public static void setStructcontext(StructContext structcontext) {
    getCurrentContext().structcontext = structcontext;
  }

  public static CounterContainer getCountercontainer() {
    return getCurrentContext().countercontainer;
  }

  public static void setCountercontainer(CounterContainer countercontainer) {
    getCurrentContext().countercontainer = countercontainer;
  }

  public static ClassesProcessor getClassprocessor() {
    return getCurrentContext().classprocessor;
  }

  public static void setClassprocessor(ClassesProcessor classprocessor) {
    getCurrentContext().classprocessor = classprocessor;
  }

  public static PoolInterceptor getPoolInterceptor() {
    return getCurrentContext().poolinterceptor;
  }

  public static void setPoolInterceptor(PoolInterceptor poolinterceptor) {
    getCurrentContext().poolinterceptor = poolinterceptor;
  }

  public static IFernflowerLogger getLogger() {
    return getCurrentContext().logger;
  }

  public static void setLogger(IFernflowerLogger logger) {
    getCurrentContext().logger = logger;
    setLogSeverity();
  }

  private static void setLogSeverity() {
    IFernflowerLogger logger = getCurrentContext().logger;

    if (logger != null) {
      String severity = (String)getProperty(IFernflowerPreferences.LOG_LEVEL);
      if (severity != null) {
        Integer iSeverity = IFernflowerLogger.mapLogLevel.get(severity.toUpperCase(Locale.US));
        if (iSeverity != null) {
          logger.setSeverity(iSeverity);
        }
      }
    }
  }

  public static String getNewLineSeparator() {
    return getOption(IFernflowerPreferences.NEW_LINE_SEPARATOR) ?
           IFernflowerPreferences.LINE_SEPARATOR_LIN : IFernflowerPreferences.LINE_SEPARATOR_WIN;
  }
}
