package com.intellij.jps.cache;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.AppExecutorUtil;

import java.util.concurrent.ExecutorService;

import static com.intellij.execution.process.ProcessIOExecutorService.INSTANCE;

public class JpsCachesPluginUtil {
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.cache.JpsCachesPluginUtil");
  public static final String PLUGIN_NAME = "jps-cache-loader";
  public static final ExecutorService EXECUTOR_SERVICE = AppExecutorUtil.createBoundedApplicationPoolExecutor("JpsCacheLoader Pool",
                                                                                                         INSTANCE, getThreadPoolSize());
  private JpsCachesPluginUtil() {}

  private static int getThreadPoolSize() {
    int threadsCount = Runtime.getRuntime().availableProcessors() - 1;
    LOG.info("Executor service will be configured with " + threadsCount + " threads");
    return threadsCount;
  }
}
