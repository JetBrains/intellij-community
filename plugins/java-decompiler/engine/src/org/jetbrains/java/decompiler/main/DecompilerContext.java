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
package org.jetbrains.java.decompiler.main;

import org.jetbrains.java.decompiler.main.collectors.BytecodeSourceMapper;
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
  public static final String CURRENT_CLASS_WRAPPER = "CURRENT_CLASS_WRAPPER";
  public static final String CURRENT_CLASS_NODE = "CURRENT_CLASS_NODE";
  public static final String CURRENT_METHOD_WRAPPER = "CURRENT_METHOD_WRAPPER";
  public static final String CURRENT_VAR_PROCESSOR = "CURRENT_VAR_PROCESSOR";

  private static final ThreadLocal<DecompilerContext> currentContext = new ThreadLocal<>();

  private final Map<String, Object> properties;
  private StructContext structContext;
  private ImportCollector importCollector;
  private VarNamesCollector varNamescollector;
  private CounterContainer counterContainer;
  private ClassesProcessor classProcessor;
  private PoolInterceptor poolInterceptor;
  private IFernflowerLogger logger;
  private BytecodeSourceMapper bytecodeSourceMapper;

  private DecompilerContext(Map<String, Object> properties) {
    this.properties = properties;
  }

  public static void initContext(Map<String, Object> propertiesCustom) {
    Map<String, Object> properties = new HashMap<>(IFernflowerPreferences.DEFAULTS);
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

  public static ImportCollector getImportCollector() {
    return getCurrentContext().importCollector;
  }

  public static void setImportCollector(ImportCollector importCollector) {
    getCurrentContext().importCollector = importCollector;
  }

  public static VarNamesCollector getVarNamesCollector() {
    return getCurrentContext().varNamescollector;
  }

  public static void setVarNamesCollector(VarNamesCollector varNamesCollector) {
    getCurrentContext().varNamescollector = varNamesCollector;
  }

  public static StructContext getStructContext() {
    return getCurrentContext().structContext;
  }

  public static void setStructContext(StructContext structContext) {
    getCurrentContext().structContext = structContext;
  }

  public static CounterContainer getCounterContainer() {
    return getCurrentContext().counterContainer;
  }

  public static void setCounterContainer(CounterContainer counterContainer) {
    getCurrentContext().counterContainer = counterContainer;
  }

  public static ClassesProcessor getClassProcessor() {
    return getCurrentContext().classProcessor;
  }

  public static void setClassProcessor(ClassesProcessor classProcessor) {
    getCurrentContext().classProcessor = classProcessor;
  }

  public static PoolInterceptor getPoolInterceptor() {
    return getCurrentContext().poolInterceptor;
  }

  public static void setPoolInterceptor(PoolInterceptor poolinterceptor) {
    getCurrentContext().poolInterceptor = poolinterceptor;
  }

  public static BytecodeSourceMapper getBytecodeSourceMapper() {
    return getCurrentContext().bytecodeSourceMapper;
  }

  public static void setBytecodeSourceMapper(BytecodeSourceMapper bytecodeSourceMapper) {
    getCurrentContext().bytecodeSourceMapper = bytecodeSourceMapper;
  }

  public static IFernflowerLogger getLogger() {
    return getCurrentContext().logger;
  }

  public static void setLogger(IFernflowerLogger logger) {
    if (logger != null) {
      String level = (String)getProperty(IFernflowerPreferences.LOG_LEVEL);
      if (level != null) {
        try {
          logger.setSeverity(IFernflowerLogger.Severity.valueOf(level.toUpperCase(Locale.US)));
        }
        catch (IllegalArgumentException ignore) { }
      }
    }
    getCurrentContext().logger = logger;
  }

  public static String getNewLineSeparator() {
    return getOption(IFernflowerPreferences.NEW_LINE_SEPARATOR) ?
           IFernflowerPreferences.LINE_SEPARATOR_UNX : IFernflowerPreferences.LINE_SEPARATOR_WIN;
  }
}