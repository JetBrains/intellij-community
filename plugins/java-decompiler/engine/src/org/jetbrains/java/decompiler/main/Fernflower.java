// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.main;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;
import org.jetbrains.java.decompiler.main.extern.*;
import org.jetbrains.java.decompiler.modules.renamer.IdentifierConverter;
import org.jetbrains.java.decompiler.modules.renamer.MemberConverterHelper;
import org.jetbrains.java.decompiler.modules.renamer.PoolInterceptor;
import org.jetbrains.java.decompiler.struct.IDecompiledData;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructContext;
import org.jetbrains.java.decompiler.struct.lazy.LazyLoader;
import org.jetbrains.java.decompiler.util.ClasspathScanner;
import org.jetbrains.java.decompiler.util.JADNameProvider;
import org.jetbrains.java.decompiler.util.TextBuffer;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class Fernflower implements IDecompiledData {
  private final StructContext structContext;
  private final ClassesProcessor classProcessor;
  private final IMemberIdentifierRenamer helper;
  private final IdentifierConverter converter;

  public Fernflower(IBytecodeProvider provider,
                    IResultSaver saver,
                    @Nullable Map<String, Object> customProperties,
                    IFernflowerLogger logger,
                    @Nullable CancellationManager cancellationManager) {
    Map<String, Object> properties = new HashMap<>(IFernflowerPreferences.DEFAULTS);
    if (customProperties != null) {
      properties.putAll(customProperties);
    }

    String level = (String)properties.get(IFernflowerPreferences.LOG_LEVEL);
    if (level != null) {
      try {
        logger.setSeverity(IFernflowerLogger.Severity.valueOf(level.toUpperCase(Locale.ENGLISH)));
      }
      catch (IllegalArgumentException ignore) { }
    }

    structContext = new StructContext(saver, this, new LazyLoader(provider));
    classProcessor = new ClassesProcessor(structContext);

    PoolInterceptor interceptor = null;
    if ("1".equals(properties.get(IFernflowerPreferences.RENAME_ENTITIES))) {
      helper = loadHelper((String)properties.get(IFernflowerPreferences.USER_RENAMER_CLASS), logger);
      interceptor = new PoolInterceptor();
      converter = new IdentifierConverter(structContext, helper, interceptor);
    }
    else {
      helper = null;
      converter = null;
    }

    IVariableNamingFactory renamerFactory = null;
    String factoryClazz = (String) properties.get(DecompilerContext.RENAMER_FACTORY);
    if (factoryClazz != null) {
      try {
        renamerFactory = Class.forName(factoryClazz).asSubclass(IVariableNamingFactory.class).getDeclaredConstructor().newInstance();
      } catch (Exception e) {
        logger.writeMessage("Error loading renamer factory class: " + factoryClazz, e);
      }
    }
    if (renamerFactory == null) {
      if("1".equals(properties.get(IFernflowerPreferences.USE_JAD_VARNAMING))) {
        boolean renameParams = "1".equals(properties.get(IFernflowerPreferences.USE_JAD_PARAMETER_RENAMING));
        renamerFactory = new JADNameProvider.JADNameProviderFactory(renameParams);
      } else {
        renamerFactory = new IdentityRenamerFactory();
      }
    }

    DecompilerContext context = new DecompilerContext(properties, logger, structContext, classProcessor, interceptor, cancellationManager, renamerFactory);

    DecompilerContext.setCurrentContext(context);

    if (DecompilerContext.getOption(IFernflowerPreferences.INCLUDE_ENTIRE_CLASSPATH)) {
      ClasspathScanner.addAllClasspath(structContext);
    }
  }

  public Fernflower(IBytecodeProvider provider, IResultSaver saver, Map<String, Object> customProperties, IFernflowerLogger logger) {
    this(provider, saver, customProperties, logger, null);
  }

  private static IMemberIdentifierRenamer loadHelper(String className, IFernflowerLogger logger) {
    if (className != null) {
      try {
        Class<?> renamerClass = Fernflower.class.getClassLoader().loadClass(className);
        return (IMemberIdentifierRenamer) renamerClass.getDeclaredConstructor().newInstance();
      }
      catch (Exception e) {
        logger.writeMessage("Cannot load renamer '" + className + "'", IFernflowerLogger.Severity.WARN, e);
      }
    }

    return new MemberConverterHelper();
  }

  public void addSource(File source) {
    structContext.addSpace(source, true);
  }

  public void addLibrary(File library) {
    structContext.addSpace(library, false);
  }

  public void decompileContext() {
    if (converter != null) {
      converter.rename();
    }

    classProcessor.loadClasses(helper);

    structContext.saveContext();
  }

  public void addToMustBeDecompiled(String prefix) {
    classProcessor.addToMustBeDecompiled(prefix);
  }

  public void clearContext() {
    DecompilerContext.setCurrentContext(null);
  }

  @Override
  public String getClassEntryName(StructClass cl, String entryName) {
    ClassNode node = classProcessor.getMapRootClasses().get(cl.qualifiedName);
    if (node == null || node.type != ClassNode.CLASS_ROOT) {
      return null;
    }
    else if (converter != null) {
      String simpleClassName = cl.qualifiedName.substring(cl.qualifiedName.lastIndexOf('/') + 1);
      return entryName.substring(0, entryName.lastIndexOf('/') + 1) + simpleClassName + ".java";
    }
    else {
      return entryName.substring(0, entryName.lastIndexOf(".class")) + ".java";
    }
  }

  @Override
  public String getClassContent(StructClass cl) {
    try {
      TextBuffer buffer = new TextBuffer(ClassesProcessor.AVERAGE_CLASS_SIZE);
      buffer.append(DecompilerContext.getProperty(IFernflowerPreferences.BANNER).toString());
      classProcessor.writeClass(cl, buffer);
      return buffer.toString();
    }
    catch (CancellationManager.CanceledException e) {
      throw e;
    }
    catch (Throwable t) {
      DecompilerContext.getLogger().writeMessage("Class " + cl.qualifiedName + " couldn't be fully decompiled.", t);
      return null;
    }
  }
}