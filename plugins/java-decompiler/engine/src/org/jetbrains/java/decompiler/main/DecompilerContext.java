// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.main;

import org.jetbrains.java.decompiler.main.collectors.BytecodeSourceMapper;
import org.jetbrains.java.decompiler.main.collectors.CounterContainer;
import org.jetbrains.java.decompiler.main.collectors.ImportCollector;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarProcessor;
import org.jetbrains.java.decompiler.modules.renamer.PoolInterceptor;
import org.jetbrains.java.decompiler.struct.StructContext;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class DecompilerContext {
  public static final String CURRENT_CLASS = "CURRENT_CLASS";
  public static final String CURRENT_CLASS_WRAPPER = "CURRENT_CLASS_WRAPPER";
  public static final String CURRENT_CLASS_NODE = "CURRENT_CLASS_NODE";
  public static final String CURRENT_METHOD_WRAPPER = "CURRENT_METHOD_WRAPPER";

  private static volatile DecompilerContext currentContext = null;

  private final Map<String, Object> properties;
  private final IFernflowerLogger logger;
  private final StructContext structContext;
  private final ClassesProcessor classProcessor;
  private final PoolInterceptor poolInterceptor;
  private ImportCollector importCollector;
  private VarProcessor varProcessor;
  private CounterContainer counterContainer;
  private BytecodeSourceMapper bytecodeSourceMapper;

  private DecompilerContext(Map<String, Object> properties,
                            IFernflowerLogger logger,
                            StructContext structContext,
                            ClassesProcessor classProcessor,
                            PoolInterceptor interceptor) {
    this.properties = properties;
    this.logger = logger;
    this.structContext = structContext;
    this.classProcessor = classProcessor;
    this.poolInterceptor = interceptor;
    this.counterContainer = new CounterContainer();
  }

  // *****************************************************************************
  // context setup and update
  // *****************************************************************************

  public static void initContext(Map<String, Object> customProperties,
                                 IFernflowerLogger logger,
                                 StructContext structContext,
                                 ClassesProcessor classProcessor,
                                 PoolInterceptor interceptor) {
    Objects.requireNonNull(logger);
    Objects.requireNonNull(structContext);
    Objects.requireNonNull(classProcessor);

    Map<String, Object> properties = new HashMap<>(IFernflowerPreferences.DEFAULTS);
    if (customProperties != null) {
      properties.putAll(customProperties);
    }

    String level = (String)properties.get(IFernflowerPreferences.LOG_LEVEL);
    if (level != null) {
      try {
        logger.setSeverity(IFernflowerLogger.Severity.valueOf(level.toUpperCase(Locale.US)));
      }
      catch (IllegalArgumentException ignore) { }
    }

    currentContext = new DecompilerContext(properties, logger, structContext, classProcessor, interceptor);
  }

  public static void clearContext() {
    currentContext = null;
  }

  public static void setProperty(String key, Object value) {
    currentContext.properties.put(key, value);
  }

  public static void startClass(ImportCollector importCollector) {
    currentContext.importCollector = importCollector;
    currentContext.counterContainer = new CounterContainer();
    currentContext.bytecodeSourceMapper = new BytecodeSourceMapper();
  }

  public static void startMethod(VarProcessor varProcessor) {
    currentContext.varProcessor = varProcessor;
    currentContext.counterContainer = new CounterContainer();
  }

  // *****************************************************************************
  // context access
  // *****************************************************************************

  public static Object getProperty(String key) {
    return currentContext.properties.get(key);
  }

  public static boolean getOption(String key) {
    return "1".equals(getProperty(key));
  }

  public static String getNewLineSeparator() {
    return getOption(IFernflowerPreferences.NEW_LINE_SEPARATOR) ?
           IFernflowerPreferences.LINE_SEPARATOR_UNX : IFernflowerPreferences.LINE_SEPARATOR_WIN;
  }

  public static IFernflowerLogger getLogger() {
    return currentContext.logger;
  }

  public static StructContext getStructContext() {
    return currentContext.structContext;
  }

  public static ClassesProcessor getClassProcessor() {
    return currentContext.classProcessor;
  }

  public static PoolInterceptor getPoolInterceptor() {
    return currentContext.poolInterceptor;
  }

  public static ImportCollector getImportCollector() {
    return currentContext.importCollector;
  }

  public static VarProcessor getVarProcessor() {
    return currentContext.varProcessor;
  }

  public static CounterContainer getCounterContainer() {
    return currentContext.counterContainer;
  }

  public static BytecodeSourceMapper getBytecodeSourceMapper() {
    return currentContext.bytecodeSourceMapper;
  }
}