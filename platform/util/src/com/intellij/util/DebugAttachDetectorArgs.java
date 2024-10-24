// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.util.Properties;

/**
 * Finds the debugger arguments passed to the IDE at startup.
 */
@ApiStatus.Internal
public final class DebugAttachDetectorArgs {
  private static final Logger LOG = Logger.getInstance(DebugAttachDetectorArgs.class);
  private static final @Nullable String DEBUG_ARGS = findDebugArgs();
  private static final @Nullable Properties AGENT_PROPERTIES = findAgentProperties();

  public static boolean isDebugEnabled() {
    return DEBUG_ARGS != null;
  }

  public static boolean isAttached() {
    if (!isDebugEnabled()) return false;
    if (!isDebugServer()) return true;

    Properties properties = AGENT_PROPERTIES;

    // for now, return true if you cannot detect
    if (properties == null) return true;

    return isAttached(properties);
  }

  public static boolean canDetectAttach() {
    return AGENT_PROPERTIES != null;
  }

  private static boolean isAttached(@NotNull Properties properties) {
    String property = properties.getProperty("sun.jdwp.listenerAddress");
    return property != null && property.isEmpty();
  }

  private static boolean isDebugServer() {
    String args = DEBUG_ARGS;
    return args != null && args.contains("server=y");
  }

  private static @Nullable String findDebugArgs() {
    try {
      for (String value : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
        if (value.contains("-agentlib:jdwp")) {
          return value;
        }
      }
    } catch (Exception e) {
      LOG.error(e);
    }
    return null;
  }

  private static @Nullable Properties findAgentProperties() {
    Class<?> vmSupportClass;
    try {
      vmSupportClass = Class.forName("jdk.internal.vm.VMSupport");
    }
    catch (Exception e) {
      try {
        vmSupportClass = Class.forName("sun.misc.VMSupport");
      }
      catch (Exception ignored) {
        LOG.warn("Unable to init DebugAttachDetector, VMSupport class not found");
        return null;
      }
    }

    try {
      return (Properties)vmSupportClass.getMethod("getAgentProperties").invoke(null);
    }
    catch (NoSuchMethodException | InvocationTargetException ex) {
      LOG.error(ex);
    }
    catch (IllegalAccessException ignored) {
    }
    return null;
  }
}
