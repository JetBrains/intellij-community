package com.intellij.ide.plugins;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.ide.startup.StartupActionScriptManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.PluginsFacade;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.GraphGenerator;
import com.intellij.util.lang.UrlClassLoader;
import com.intellij.util.text.StringTokenizer;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import sun.reflect.Reflection;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author mike
 */
@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToPrintStackTrace"}) // No logger is loaded at this time so we have to use these.
public class PluginManager {
  @SuppressWarnings({"NonConstantLogger"}) //Logger is lasy-initialized in order not to use it outside the appClassLoader
  private static Logger ourLogger = null;
  @NonNls public static final String AREA_IDEA_PROJECT = "IDEA_PROJECT";
  @NonNls public static final String AREA_IDEA_MODULE = "IDEA_MODULE";
  @NonNls private static final String PROPERTY_IGNORE_CLASSPATH = "ignore.classpath";
  @NonNls private static final String PROPERTY_PLUGIN_PATH = "plugin.path";

  private static Logger getLogger() {
    if (ourLogger == null) {
      ourLogger = Logger.getInstance("#com.intellij.ide.plugins.PluginManager");
    }
    return ourLogger;
  }

  // these fields are accessed via Reflection, so their names must not be changed by the obfuscator
  // do not make them private cause in this case they will be scrambled
  static String[] ourArguments;
  static String ourMainClass;
  static String ourMethodName;

  private static class Facade extends PluginsFacade {
    public IdeaPluginDescriptor getPlugin(PluginId id) {
      return PluginManager.getPlugin(id);
    }

    public IdeaPluginDescriptor[] getPlugins() {
      return PluginManager.getPlugins();
    }
  }

  private static IdeaPluginDescriptorImpl[] ourPlugins;
  private static Map<String, PluginId> ourPluginClasses;

  public static void main(final String[] args, final String mainClass, final String methodName) {
    main(args, mainClass, methodName, new ArrayList<URL>());
  }

  public static void main(final String[] args, final String mainClass, final String methodName, List<URL> classpathElements) {
    ourArguments = args;
    ourMainClass = mainClass;
    ourMethodName = methodName;

    final PluginManager pluginManager = new PluginManager();
    pluginManager.bootstrap(classpathElements);
  }

  /**
   * do not call this method during bootstrap, should be called in a copy of PluginManager, loaded by IdeaClassLoader
   */
  public synchronized static IdeaPluginDescriptor[] getPlugins() {
    if (ourPlugins == null) {
      initializePlugins();
    }
    return ourPlugins;
  }

  private static void initializePlugins() {
    configureExtensions();
    
    if (!shouldLoadPlugins()) {
      ourPlugins = new IdeaPluginDescriptorImpl[0];
      return;
    }

    final IdeaPluginDescriptorImpl[] pluginDescriptors = loadDescriptors();

    final Map<PluginId, IdeaPluginDescriptor> idToDescriptorMap = new HashMap<PluginId, IdeaPluginDescriptor>();
    for (final IdeaPluginDescriptor descriptor : pluginDescriptors) {
      idToDescriptorMap.put(descriptor.getPluginId(), descriptor);
    }
    // sort descriptors according to plugin dependencies
    Arrays.sort(pluginDescriptors, getPluginDescriptorComparator(idToDescriptorMap));

    final Class callerClass = Reflection.getCallerClass(1);
    final ClassLoader parentLoader = callerClass.getClassLoader();
    for (final IdeaPluginDescriptorImpl pluginDescriptor : pluginDescriptors) {
      final List<File> classPath = pluginDescriptor.getClassPath();
      final PluginId[] dependentPluginIds = pluginDescriptor.getDependentPluginIds();
      final ClassLoader[] parentLoaders = getParentLoaders(idToDescriptorMap, dependentPluginIds);

      final ClassLoader pluginClassLoader = createPluginClassLoader(classPath.toArray(new File[classPath.size()]),
                                                                    parentLoaders.length > 0 ? parentLoaders : new ClassLoader[] {parentLoader},
                                                                    pluginDescriptor);
      pluginDescriptor.setLoader(pluginClassLoader);
      pluginDescriptor.registerExtensions();
    }

    ourPlugins = pluginDescriptors;
  }

  private static void configureExtensions() {
    final List<Element> extensionPoints = new ArrayList<Element>();
    final List<Element> extensions = new ArrayList<Element>();

    try {
      final InputStream stdExtensionsStream = PluginManager.class.getResourceAsStream("/standard-extensions.xml");
      final Document stdExtensions = JDOMUtil.loadDocument(stdExtensionsStream);
      final Element root = stdExtensions.getRootElement();
      final List<Element> epRoots = JDOMUtil.getChildrenFromAllNamespaces(root, "extensionPoints");
      for (Element epRoot : epRoots) {
        for (Object o : epRoot.getChildren()) {
          extensionPoints.add((Element)o);
        }
      }

      final List<Element> extensionRoots = JDOMUtil.getChildrenFromAllNamespaces(root, "extensions");
      for (Element extRoot : extensionRoots) {
        for (Object o : extRoot.getChildren()) {
          extensions.add((Element)o);
        }
      }
    }
    catch (JDOMException e) {
      e.printStackTrace();
    }
    catch (IOException e) {
      e.printStackTrace();
    }

    Extensions.setLogProvider(new IdeaLogProvider());
    Extensions.registerAreaClass(AREA_IDEA_PROJECT, null);
    Extensions.registerAreaClass(AREA_IDEA_MODULE, AREA_IDEA_PROJECT);

    Extensions.getRootArea().registerAreaExtensionsAndPoints(RootPluginDescriptor.INSTANCE, extensionPoints, extensions);

    Extensions.getRootArea().getExtensionPoint(Extensions.AREA_LISTENER_EXTENSION_POINT).registerExtension(new AreaListener() {
      public void areaCreated(String areaClass, AreaInstance areaInstance) {
        if (AREA_IDEA_PROJECT.equals(areaClass) || AREA_IDEA_MODULE.equals(areaClass)) {
          final ExtensionsArea area = Extensions.getArea(areaInstance);
          area.registerAreaExtensionsAndPoints(RootPluginDescriptor.INSTANCE, extensionPoints, extensions);
        }
      }

      public void areaDisposing(String areaClass, AreaInstance areaInstance) {
      }
    }, LoadingOrder.FIRST);
  }

  public static boolean shouldLoadPlugins() {
    try {
      // no plugins during bootstrap
      Class.forName("com.intellij.openapi.extensions.Extensions");
    }
    catch (ClassNotFoundException e) {
      return false;
    }
    //noinspection HardCodedStringLiteral
    final String loadPlugins = System.getProperty("idea.load.plugins");
    return loadPlugins == null || Boolean.TRUE.toString().equals(loadPlugins);
  }

  public static boolean shouldLoadPlugin(IdeaPluginDescriptor descriptor) {
    //noinspection HardCodedStringLiteral
    final String loadPluginCategory = System.getProperty("idea.load.plugins.category");
    return loadPluginCategory == null || loadPluginCategory.equals(descriptor.getCategory());
  }


  private static Comparator<IdeaPluginDescriptor> getPluginDescriptorComparator(Map<PluginId, IdeaPluginDescriptor> idToDescriptorMap) {
    final Graph<PluginId> graph = createPluginIdGraph(idToDescriptorMap);
    final DFSTBuilder<PluginId> builder = new DFSTBuilder<PluginId>(graph);
    /*
    if (!builder.isAcyclic()) {
      final Pair<String,String> circularDependency = builder.getCircularDependency();
      throw new Exception("Cyclic dependencies between plugins are not allowed: \"" + circularDependency.getFirst() + "\" and \"" + circularDependency.getSecond() + "");
    }
    */
    final Comparator<PluginId> idComparator = builder.comparator();
    return new Comparator<IdeaPluginDescriptor>() {
      public int compare(IdeaPluginDescriptor o1, IdeaPluginDescriptor o2) {
        return idComparator.compare(o1.getPluginId(), o2.getPluginId());
      }
    };
  }

  private static Graph<PluginId> createPluginIdGraph(final Map<PluginId, IdeaPluginDescriptor> idToDescriptorMap) {
    final PluginId[] ids = idToDescriptorMap.keySet().toArray(new PluginId[idToDescriptorMap.size()]);
    return GraphGenerator.create(CachingSemiGraph.create(new GraphGenerator.SemiGraph<PluginId>() {
      public Collection<PluginId> getNodes() {
        return Arrays.asList(ids);
      }

      public Iterator<PluginId> getIn(PluginId pluginId) {
        final IdeaPluginDescriptor descriptor = idToDescriptorMap.get(pluginId);
        return Arrays.asList(descriptor.getDependentPluginIds()).iterator();
      }
    }));
  }

  private static ClassLoader[] getParentLoaders(Map<PluginId, IdeaPluginDescriptor> idToDescriptorMap, PluginId[] pluginIds) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return new ClassLoader[0];
    final List<ClassLoader> classLoaders = new ArrayList<ClassLoader>();
    for (final PluginId id : pluginIds) {
      IdeaPluginDescriptor pluginDescriptor = idToDescriptorMap.get(id);
      if (pluginDescriptor == null) {
        continue; // Might be an optional dependency
      }

      final ClassLoader loader = pluginDescriptor.getPluginClassLoader();
      if (loader == null) {
        getLogger().assertTrue(false, "Plugin class loader should be initialized for plugin " + id);
      }
      classLoaders.add(loader);
    }
    return classLoaders.toArray(new ClassLoader[classLoaders.size()]);
  }

  /**
   * Called via reflection
   */
  protected static void start() {
    try {
      //noinspection HardCodedStringLiteral
      ThreadGroup threadGroup = new ThreadGroup("Idea Thread Group") {
        public void uncaughtException(Thread t, Throwable e) {
          getLogger().error(e);
        }
      };

      Runnable runnable = new Runnable() {
        public void run() {
          try {
            //noinspection AssignmentToStaticFieldFromInstanceMethod
            PluginsFacade.INSTANCE = new Facade();

            Class aClass = Class.forName(ourMainClass);
            final Method method = aClass.getDeclaredMethod(ourMethodName, (ArrayUtil.EMPTY_STRING_ARRAY).getClass());
            method.setAccessible(true);

            method.invoke(null, new Object[]{ourArguments});
          }
          catch (Exception e) {
            e.printStackTrace();
            getLogger().error("Error while accessing " + ourMainClass + "." + ourMethodName + " with arguments: " + Arrays.asList(ourArguments), e);
          }
        }
      };

      //noinspection HardCodedStringLiteral
      new Thread(threadGroup, runnable, "Idea Main Thread").start();
    }
    catch (Exception e) {
      getLogger().error(e);
    }
  }

  private static IdeaPluginDescriptorImpl[] loadDescriptors() {
    if (isLoadingOfExternalPluginsDisabled()) {
      return IdeaPluginDescriptorImpl.EMPTY_ARRAY;
    }

    final List<IdeaPluginDescriptorImpl> result = new ArrayList<IdeaPluginDescriptorImpl>();

    loadDescriptors(PathManager.getPluginsPath(), result);
    loadDescriptors(PathManager.getPreinstalledPluginsPath(), result);

    loadDescriptorsFromProperty(result);

    loadDescriptorsFromClassPath(result);

    String errorMessage = filterBadPlugins(result);

    IdeaPluginDescriptorImpl[] pluginDescriptors = result.toArray(new IdeaPluginDescriptorImpl[result.size()]);
    try {
      Arrays.sort(pluginDescriptors, new PluginDescriptorComparator(pluginDescriptors));
    }
    catch (Exception e) {
      errorMessage = IdeBundle.message("error.plugins.were.not.loaded", e.getMessage());
      pluginDescriptors = IdeaPluginDescriptorImpl.EMPTY_ARRAY;
    }
    if (errorMessage != null) {
      JOptionPane.showMessageDialog(null, errorMessage, IdeBundle.message("title.plugin.error"), JOptionPane.ERROR_MESSAGE);
    }
    return pluginDescriptors;
  }

  private static void loadDescriptorsFromProperty(final List<IdeaPluginDescriptorImpl> result) {
    final String pathProperty = System.getProperty(PROPERTY_PLUGIN_PATH);
    if (pathProperty == null) return;

    for (java.util.StringTokenizer t = new java.util.StringTokenizer(pathProperty, File.pathSeparator); t.hasMoreTokens();) {
      String s = t.nextToken();
      final IdeaPluginDescriptorImpl ideaPluginDescriptor = loadDescriptor(new File(s));
      if (ideaPluginDescriptor != null) {
        result.add(ideaPluginDescriptor);
      }
    }
  }

  @SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToPrintStackTrace"})
  private static void loadDescriptorsFromClassPath(final List<IdeaPluginDescriptorImpl> result) {
    try {
      final String homePath = PathManager.getHomePath();
      final Collection<URL> urls = getClassLoaderUrls();
      for (URL url : urls) {
        final String protocol = url.getProtocol();
        if ("file".equals(protocol)) {
          final File file = new File(URLDecoder.decode(url.getFile()));
          final String canonicalPath = file.getCanonicalPath();
          //if (!canonicalPath.startsWith(homePath) || canonicalPath.endsWith(".jar")) continue;
          //if (!canonicalPath.startsWith(homePath)) continue;
          final IdeaPluginDescriptorImpl pluginDescriptor = loadDescriptor(file);
          if (pluginDescriptor != null && !result.contains(pluginDescriptor)) {
            result.add(pluginDescriptor);
          }
        }
      }
    }
    catch (Exception e) {
      System.err.println("Error loading plugins from classpath:");
      e.printStackTrace();
    }
  }

  private static Collection<URL> getClassLoaderUrls() {
    final ClassLoader classLoader = PluginManager.class.getClassLoader();
    final Class<? extends ClassLoader> aClass = classLoader.getClass();
    if (aClass.getName().equals(UrlClassLoader.class.getName())) {
      try {
        final List<URL> urls = (List<URL>)aClass.getDeclaredMethod("getUrls").invoke(classLoader);
        return urls;
      }
      catch (IllegalAccessException e) {
      }
      catch (InvocationTargetException e) {
      }
      catch (NoSuchMethodException e) {
      }
    }
    if (classLoader instanceof URLClassLoader) {
      return Arrays.asList(((URLClassLoader)classLoader).getURLs());
    }

    return Collections.emptyList();
  }

  private static String filterBadPlugins(List<IdeaPluginDescriptorImpl> result) {
    final Map<PluginId, IdeaPluginDescriptorImpl> idToDescriptorMap = new HashMap<PluginId, IdeaPluginDescriptorImpl>();
    final StringBuffer message = new StringBuffer();
    boolean pluginsWithoutIdFound = false;
    for (Iterator<IdeaPluginDescriptorImpl> it = result.iterator(); it.hasNext();) {
      final IdeaPluginDescriptorImpl descriptor = it.next();
      final PluginId id = descriptor.getPluginId();
      if (idToDescriptorMap.containsKey(id)) {
        if (message.length() > 0) {
          message.append("\n");
        }
        message.append(IdeBundle.message("message.duplicate.plugin.id"));
        message.append(id);
        it.remove();
      }
      else {
        idToDescriptorMap.put(id, descriptor);
      }
    }
    for (Iterator<IdeaPluginDescriptorImpl> it = result.iterator(); it.hasNext();) {
      final IdeaPluginDescriptor pluginDescriptor = it.next();
      final PluginId[] dependentPluginIds = pluginDescriptor.getDependentPluginIds();
      final Set<PluginId> optionalDependencies = new HashSet<PluginId>(Arrays.asList(pluginDescriptor.getDependentPluginIds()));
      for (final PluginId dependentPluginId : dependentPluginIds) {
        if (!idToDescriptorMap.containsKey(dependentPluginId) && !optionalDependencies.contains(dependentPluginId)) {
          if (message.length() > 0) {
            message.append("\n");
          }
          message.append(IdeBundle.message("error.required.plugin.not.found", pluginDescriptor.getPluginId(), dependentPluginId));
          it.remove();
          break;
        }
      }
    }
    if (pluginsWithoutIdFound) {
      if (message.length() > 0) {
        message.append("\n");
      }
      message.append(IdeBundle.message("error.plugins.without.id.found"));
    }
    if (message.length() > 0) {
      message.insert(0, IdeBundle.message("error.problems.found.loading.plugins"));
      return message.toString();
    }
    for (Iterator<IdeaPluginDescriptorImpl> iterator = result.iterator(); iterator.hasNext();) {
      IdeaPluginDescriptor descriptor = iterator.next();
      if (!shouldLoadPlugins() || !shouldLoadPlugin(descriptor)) {
        iterator.remove();
      }
    }
    return null;
  }

  private static void loadDescriptors(String pluginsPath, List<IdeaPluginDescriptorImpl> result) {
    final File pluginsHome = new File(pluginsPath);
    final File[] files = pluginsHome.listFiles();
    if (files != null) {
      for (File file : files) {
        final IdeaPluginDescriptorImpl descriptor = loadDescriptor(file);
        if (descriptor != null && !result.contains(descriptor)) {
          result.add(descriptor);
        }
      }
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static IdeaPluginDescriptorImpl loadDescriptor(final File file) {
    IdeaPluginDescriptorImpl descriptor = null;

    if (file.isDirectory()) {
      descriptor = loadDescriptorFromDir(file);

      if (descriptor == null) {
       File libDir = new File(file, "lib");
       if (!libDir.isDirectory()) {
         return null;
       }
       final File[] files = libDir.listFiles();
       if (files == null || files.length == 0) {
         return null;
       }
       for (final File f : files) {
         final String lowercasedName = f.getName().toLowerCase();
         if (lowercasedName.endsWith(".jar") || lowercasedName.endsWith(".zip") && f.isFile()) {
           IdeaPluginDescriptorImpl descriptor1 = loadDescriptorFromJar(f);
           if (descriptor1 != null) {
             if (descriptor != null) {
               getLogger().info("Cannot load " + file + " because two or more plugin.xml's detected");
               return null;
             }
             descriptor = descriptor1;
             descriptor.setPath(file);
           }
         }
         else if (f.isDirectory()) {
           IdeaPluginDescriptorImpl descriptor1 = loadDescriptorFromDir(f);
           if (descriptor1 != null) {
             if (descriptor != null) {
               getLogger().info("Cannot load " + file + " because two or more plugin.xml's detected");
               return null;
             }
             descriptor = descriptor1;
             descriptor.setPath(file);
           }
         }
       }
     }
    }
    else if (file.getName().endsWith(".jar")) {
      descriptor = loadDescriptorFromJar(file);
    }

    return descriptor;
  }

  private static IdeaPluginDescriptorImpl loadDescriptorFromDir(final File file) {
    IdeaPluginDescriptorImpl descriptor = null;
    File descriptorFile = new File(file, "META-INF" + File.separator + "plugin.xml");
    if (descriptorFile.exists()) {
      descriptor = new IdeaPluginDescriptorImpl(file);

      try {
        descriptor.readExternal(JDOMUtil.loadDocument(descriptorFile).getRootElement());
      }
      catch (Exception e) {
        System.err.println("Cannot load: " + descriptorFile.getAbsolutePath());
        e.printStackTrace();
      }
    }
    return descriptor;
  }

  private static IdeaPluginDescriptorImpl loadDescriptorFromJar(File file) {
    IdeaPluginDescriptorImpl descriptor = null;
    try {
      ZipFile zipFile = new ZipFile(file);

      try {
        //noinspection HardCodedStringLiteral
        final ZipEntry entry = zipFile.getEntry("META-INF/plugin.xml");
        if (entry != null) {
          descriptor = new IdeaPluginDescriptorImpl(file);

          final InputStream inputStream = zipFile.getInputStream(entry);
          final Element rootElement = JDOMUtil.loadDocument(inputStream).getRootElement();
          inputStream.close();
          descriptor.readExternal(rootElement);
        }
      }
      finally {
        zipFile.close();
      }
    }
    catch (Exception e) {
      getLogger().info("Cannot load " + file, e);
    }
    return descriptor;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  protected void bootstrap(List<URL> classpathElements) {
    UrlClassLoader newClassLoader = initClassloader(classpathElements);
    try {
      final Class mainClass = Class.forName(getClass().getName(), true, newClassLoader);
      Field field = mainClass.getDeclaredField("ourMainClass");
      field.setAccessible(true);
      field.set(null, ourMainClass);

      field = mainClass.getDeclaredField("ourMethodName");
      field.setAccessible(true);
      field.set(null, ourMethodName);

      field = mainClass.getDeclaredField("ourArguments");
      field.setAccessible(true);
      field.set(null, ourArguments);

      final Method startMethod = mainClass.getDeclaredMethod("start");
      startMethod.setAccessible(true);
      startMethod.invoke(null, ArrayUtil.EMPTY_OBJECT_ARRAY);
    }
    catch (Exception e) {
      Logger logger = getLogger();
      if (logger == null) {
        e.printStackTrace(System.err);
      }
      else {
        logger.error(e);
      }
    }
  }

  public UrlClassLoader initClassloader(final List<URL> classpathElements) {
    PathManager.loadProperties();

    try {
      addParentClasspath(classpathElements);
      addIDEALibraries(classpathElements);
      addAdditionalClassPath(classpathElements);
    }
    catch (IllegalArgumentException e) {
      JOptionPane
        .showMessageDialog(JOptionPane.getRootFrame(), e.getMessage(), CommonBundle.getErrorTitle(), JOptionPane.INFORMATION_MESSAGE);
      System.exit(1);
    }
    catch (MalformedURLException e) {
      JOptionPane
        .showMessageDialog(JOptionPane.getRootFrame(), e.getMessage(), CommonBundle.getErrorTitle(), JOptionPane.INFORMATION_MESSAGE);
      System.exit(1);
    }

    filterClassPath(classpathElements);

    UrlClassLoader newClassLoader = null;
    try {
      newClassLoader = new UrlClassLoader(classpathElements, null, true, true);

      // prepare plugins
      if (!isLoadingOfExternalPluginsDisabled()) {
        PluginInstaller.initPluginClasses();
        StartupActionScriptManager.executeActionScript();
      }

      Thread.currentThread().setContextClassLoader(newClassLoader);

    }
    catch (Exception e) {
      Logger logger = getLogger();
      if (logger == null) {
        e.printStackTrace(System.err);
      }
      else {
        logger.error(e);
      }
    }
    return newClassLoader;
  }

  private static void filterClassPath(final List<URL> classpathElements) {
    final String ignoreProperty = System.getProperty(PROPERTY_IGNORE_CLASSPATH);
    if (ignoreProperty == null) return;

    final Pattern pattern = Pattern.compile(ignoreProperty);

    for (Iterator<URL> i = classpathElements.iterator(); i.hasNext();) {
      URL url = i.next();
      final String u = url.toExternalForm();
      if (pattern.matcher(u).matches()) {
        i.remove();
      }
    }
  }

  private static ClassLoader createPluginClassLoader(final File[] classPath,
                                                         final ClassLoader[] parentLoaders,
                                                         IdeaPluginDescriptor pluginDescriptor) {

    if (pluginDescriptor.getUseIdeaClassLoader()) {
      try {
        final ClassLoader loader = PluginManager.class.getClassLoader();
        final Method addUrlMethod = getAddUrlMethod(loader);


        for (File aClassPath : classPath) {
          final File file = aClassPath.getCanonicalFile();
          addUrlMethod.invoke(loader,  file.toURL());
        }

        return loader;
      }
      catch (NoSuchMethodException e) {
        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      }
      catch (IOException e) {
        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      }
      catch (IllegalAccessException e) {
        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      }
      catch (InvocationTargetException e) {
        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      }
    }

    PluginId pluginId = pluginDescriptor.getPluginId();
    File pluginRoot = pluginDescriptor.getPath();

    if (ApplicationManager.getApplication().isUnitTestMode()) return null;
    try {
      final List<URL> urls = new ArrayList<URL>(classPath.length);
      for (File aClassPath : classPath) {
        final File file = aClassPath.getCanonicalFile(); // it is critical not to have "." and ".." in classpath elements
        urls.add(file.toURL());
      }
      return new PluginClassLoader(urls, parentLoaders, pluginId, pluginRoot);
    }
    catch (MalformedURLException e) {
      e.printStackTrace();
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  private static Method getAddUrlMethod(final ClassLoader loader) throws NoSuchMethodException {
    if (loader instanceof URLClassLoader) {
      final Method addUrlMethod = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
      addUrlMethod.setAccessible(true);
      return addUrlMethod;
    }
    
    return loader.getClass().getDeclaredMethod("addURL", URL.class);
  }


  private void addParentClasspath(List<URL> aClasspathElements) throws MalformedURLException {
    final ClassLoader loader = getClass().getClassLoader();
    if (loader instanceof URLClassLoader) {
      URLClassLoader urlClassLoader = (URLClassLoader)loader;
      aClasspathElements.addAll(Arrays.asList(urlClassLoader.getURLs()));
    }
    else {
      try {
        Class antClassLoaderClass = Class.forName("org.apache.tools.ant.AntClassLoader");
        if (antClassLoaderClass.isInstance(loader) ||
            loader.getClass().getName().equals("org.apache.tools.ant.AntClassLoader") ||
            loader.getClass().getName().equals("org.apache.tools.ant.loader.AntClassLoader2")) {
          //noinspection HardCodedStringLiteral
          final String classpath =
            (String)antClassLoaderClass.getDeclaredMethod("getClasspath", ArrayUtil.EMPTY_CLASS_ARRAY).invoke(loader, ArrayUtil.EMPTY_OBJECT_ARRAY);
          final StringTokenizer tokenizer = new StringTokenizer(classpath, File.separator, false);
          while (tokenizer.hasMoreTokens()) {
            final String token = tokenizer.nextToken();
            aClasspathElements.add(new File(token).toURL());
          }
        }
        else {
          getLogger().warn("Unknown classloader: " + loader.getClass().getName());
        }
      }
      catch (ClassCastException e) {
        getLogger().warn("Unknown classloader [CCE]: " + e.getMessage());
      }
      catch (ClassNotFoundException e) {
        getLogger().warn("Unknown classloader [CNFE]: " + loader.getClass().getName());
      }
      catch (NoSuchMethodException e) {
        getLogger().warn("Unknown classloader [NSME]: " + e.getMessage());
      }
      catch (IllegalAccessException e) {
        getLogger().warn("Unknown classloader [IAE]: " + e.getMessage());
      }
      catch (InvocationTargetException e) {
        getLogger().warn("Unknown classloader [ITE]: " + e.getMessage());
      }
    }
  }

  private static void addIDEALibraries(List<URL> classpathElements) {
    final String ideaHomePath = PathManager.getHomePath();
    addAllFromLibFolder(ideaHomePath, classpathElements);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static void addAllFromLibFolder(final String aFolderPath, List<URL> classPath) {
    try {
      final Class<PluginManager> aClass = PluginManager.class;
      final String selfRoot = PathManager.getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");

      final URL selfRootUrl = new File(selfRoot).getAbsoluteFile().toURL();
      classPath.add(selfRootUrl);

      final File libFolder = new File(aFolderPath + File.separator + "lib");
      addLibraries(classPath, libFolder, selfRootUrl);

      final File antLib = new File(new File(libFolder, "ant"), "lib");
      addLibraries(classPath, antLib, selfRootUrl);
    }
    catch (MalformedURLException e) {
      getLogger().error(e);
    }
  }

  private static void addLibraries(List<URL> classPath, File fromDir, final URL selfRootUrl) throws MalformedURLException {
    final File[] files = fromDir.listFiles();
    if (files != null) {
      for (final File file : files) {
        if (!isJarOrZip(file)) {
          continue;
        }
        final URL url = file.toURL();
        if (selfRootUrl.equals(url)) {
          continue;
        }
        classPath.add(url);
      }
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static boolean isJarOrZip(File file) {
    if (file.isDirectory()) {
      return false;
    }
    final String name = file.getName().toLowerCase();
    return name.endsWith(".jar") || name.endsWith(".zip");
  }

  private static void addAdditionalClassPath(List<URL> classPath) {
    try {
      //noinspection HardCodedStringLiteral
      final StringTokenizer tokenizer = new StringTokenizer(System.getProperty("idea.additional.classpath", ""), File.pathSeparator, false);
      while (tokenizer.hasMoreTokens()) {
        String pathItem = tokenizer.nextToken();
        classPath.add(new File(pathItem).toURL());
      }
    }
    catch (MalformedURLException e) {
      getLogger().error(e);
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static boolean isLoadingOfExternalPluginsDisabled() {
    return !"true".equalsIgnoreCase(System.getProperty("idea.plugins.load", "true"));
  }

  public static boolean isPluginInstalled(PluginId id) {
    return (getPlugin(id) != null);
  }

  public static IdeaPluginDescriptor getPlugin(PluginId id) {
    final IdeaPluginDescriptor[] plugins = getPlugins();
    for (final IdeaPluginDescriptor plugin : plugins) {
      if (Comparing.equal(id, plugin.getPluginId())) {
        return plugin;
      }
    }
    return null;
  }

  public static void addPluginClass(String className, PluginId pluginId) {
    if (ourPluginClasses == null) {
      ourPluginClasses = new HashMap<String, PluginId>();
    }
    ourPluginClasses.put(className, pluginId);
  }

  public static boolean isPluginClass(String className) {
    return getPluginByClassName(className) != null;
  }

  @Nullable
  public static PluginId getPluginByClassName(String className) {
    return ourPluginClasses != null ? ourPluginClasses.get(className) : null;
  }

  private static class IdeaLogProvider implements LogProvider {
    public void error(String message) {
      getLogger().error(message);
    }

    public void error(String message, Throwable t) {
      getLogger().error(message, t);
    }

    public void error(Throwable t) {
      getLogger().error(t);
    }

    public void warn(String message) {
      getLogger().info(message);
    }

    public void warn(String message, Throwable t) {
      getLogger().info(message, t);
    }

    public void warn(Throwable t) {
      getLogger().info(t);
    }
  }
}
