package com.intellij.lang.ant.config.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.macro.Macro;
import com.intellij.ide.macro.MacroManager;
import com.intellij.lang.ant.config.*;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.NewInstanceFactory;
import com.intellij.util.config.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

public class AntBuildFileImpl implements AntBuildFileBase {

  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.config.impl.AntBuildFileImpl");

  public static final AbstractProperty<AntInstallation> ANT_INSTALLATION = new AbstractProperty<AntInstallation>() {
    public String getName() {
      return "$antInstallation";
    }

    public AntInstallation getDefault(final AbstractPropertyContainer container) {
      return GlobalAntConfiguration.INSTANCE.get(container).getBundledAnt();
    }

    public AntInstallation copy(final AntInstallation value) {
      return value;
    }

    public AntInstallation get(final AbstractPropertyContainer container) {
      if (container.hasProperty(ANT_REFERENCE)) {
        return RUN_WITH_ANT.get(container);
      }
      return GlobalAntConfiguration.INSTANCE.get(container).getBundledAnt();
    }
  };

  public static final AbstractProperty<List<File>> ALL_CLASS_PATH = new AbstractProperty<List<File>>() {
    public String getName() {
      return "$allClasspath";
    }

    public List<File> getDefault(AbstractProperty.AbstractPropertyContainer container) {
      return get(container);
    }

    public List<File> get(AbstractProperty.AbstractPropertyContainer container) {
      ArrayList<File> classpath = new ArrayList<File>();
      collectClasspath(classpath, ADDITIONAL_CLASSPATH, container);
      AntInstallation antInstallation = ANT_INSTALLATION.get(container);
      if (antInstallation != null) {
        collectClasspath(classpath, AntInstallation.CLASS_PATH, antInstallation.getProperties());
      }

      return classpath;
    }

    private void collectClasspath(ArrayList<File> files,
                                  ListProperty<AntClasspathEntry> property,
                                  AbstractProperty.AbstractPropertyContainer container) {
      if (!container.hasProperty(property)) return;
      Iterator<AntClasspathEntry> entries = property.getIterator(container);
      while (entries.hasNext()) {
        AntClasspathEntry entry = entries.next();
        entry.addFilesTo(files);
      }
    }

    public void set(AbstractProperty.AbstractPropertyContainer container, List<File> files) {
      throw new UnsupportedOperationException(getName());
    }

    public List<File> copy(List<File> files) {
      return files;
    }
  };

  public static final BooleanProperty RUN_IN_BACKGROUND = new BooleanProperty("runInBackground", false);
  public static final IntProperty MAX_HEAP_SIZE = new IntProperty("maximumHeapSize", 128);
  public static final BooleanProperty VERBOSE = new BooleanProperty("verbose", true);
  public static final BooleanProperty TREE_VIEW = new BooleanProperty("treeView", true);
  public static final BooleanProperty CLOSE_ON_NO_ERRORS = new BooleanProperty("viewClosedWhenNoErrors", false);
  public static final AbstractProperty<String> CUSTOM_JDK_NAME = new StringProperty("customJdkName", "");
  public static final ListProperty<TargetFilter> TARGET_FILTERS = ListProperty.create("targetFilters");
  public static final ListProperty<BuildFileProperty> ANT_PROPERTIES = ListProperty.create("properties");
  public static final AbstractProperty<String> ANT_COMMAND_LINE_PARAMETERS = new StringProperty("antCommandLine", "");
  public static final AbstractProperty<AntReference> ANT_REFERENCE =
    new ValueProperty<AntReference>("antReference", AntReference.PROJECT_DEFAULT);
  public static final ListProperty<AntClasspathEntry> ADDITIONAL_CLASSPATH = ListProperty.create("additionalClassPath");
  public static final AbstractProperty<AntInstallation> RUN_WITH_ANT = new AbstractProperty<AntInstallation>() {
    public String getName() {
      return "$runWithAnt";
    }

    @Nullable
    public AntInstallation getDefault(AbstractProperty.AbstractPropertyContainer container) {
      return get(container);
    }

    @Nullable
    public AntInstallation get(AbstractProperty.AbstractPropertyContainer container) {
      return AntReference.findAnt(ANT_REFERENCE, container);
    }

    public AntInstallation copy(AntInstallation antInstallation) {
      return antInstallation;
    }
  };

  private static final ListProperty<AntClasspathEntry> ALL_CLASS_PATH_ENTRIES =
    new ListProperty<AntClasspathEntry>("$allClasspathEntries") {
      public ArrayList<AntClasspathEntry> getModifiableList(AbstractPropertyContainer container) {
        LOG.error("shouldNotCall");
        return new ArrayList<AntClasspathEntry>();
      }

      public List<AntClasspathEntry> getDefault(AbstractPropertyContainer container) {
        return get(container);
      }

      public List<AntClasspathEntry> get(AbstractProperty.AbstractPropertyContainer container) {
        ArrayList<AntClasspathEntry> entries = new ArrayList<AntClasspathEntry>();
        AntInstallation antInstallation = RUN_WITH_ANT.get(container);
        if (antInstallation == null) return Collections.emptyList();
        entries.addAll(AntInstallation.CLASS_PATH.get(antInstallation.getProperties()));
        entries.addAll(ADDITIONAL_CLASSPATH.get(container));
        return entries;
      }
    };

  private final AntFile myFile;
  private final AntConfigurationBase myAntConfiguration;
  private final ExternalizablePropertyContainer myWorkspaceOptions;
  private final ExternalizablePropertyContainer myProjectOptions;
  private final AbstractProperty.AbstractPropertyContainer myAllOptions;
  private final AntClassLoaderHolder myClassloaderHolder;
  private boolean myExpandFirstTime = true;

  public AntBuildFileImpl(final AntFile antFile, final AntConfigurationBase configuration) {
    myFile = antFile;
    antFile.getSourceElement().putCopyableUserData(XmlFile.ANT_BUILD_FILE, this);
    myAntConfiguration = configuration;
    myWorkspaceOptions = new ExternalizablePropertyContainer();
    myWorkspaceOptions.registerProperty(RUN_IN_BACKGROUND);
    myWorkspaceOptions.registerProperty(CLOSE_ON_NO_ERRORS);
    myWorkspaceOptions.registerProperty(TREE_VIEW);
    myWorkspaceOptions.registerProperty(VERBOSE);
    myWorkspaceOptions.registerProperty(TARGET_FILTERS, "filter", NewInstanceFactory.fromClass(TargetFilter.class));
    myWorkspaceOptions.registerProperty((StringProperty)ANT_COMMAND_LINE_PARAMETERS);

    myWorkspaceOptions.rememberKey(RUN_WITH_ANT);
    myWorkspaceOptions.rememberKey(ALL_CLASS_PATH_ENTRIES);

    myProjectOptions = new ExternalizablePropertyContainer();
    myProjectOptions.registerProperty(MAX_HEAP_SIZE);
    myProjectOptions.registerProperty((StringProperty)CUSTOM_JDK_NAME);
    myProjectOptions.registerProperty(ANT_PROPERTIES, "property", NewInstanceFactory.fromClass(BuildFileProperty.class));
    myProjectOptions.registerProperty(ADDITIONAL_CLASSPATH, "entry", SinglePathEntry.EXTERNALIZER);
    myProjectOptions.registerProperty(ANT_REFERENCE, AntReference.EXTERNALIZER);

    myAllOptions = new CompositePropertyContainer(new AbstractProperty.AbstractPropertyContainer[]{myWorkspaceOptions, myProjectOptions,
      GlobalAntConfiguration.getInstance().getProperties(getProject())});

    myClassloaderHolder = new AntClassLoaderHolder(myAllOptions, ALL_CLASS_PATH_ENTRIES);
  }

  @Nullable
  public String getPresentableName() {
    AntBuildModel model = myAntConfiguration.getModelIfRegistered(this);
    String name = model != null ? model.getName() : null;
    if (name == null || name.trim().length() == 0) {
      name = myFile.getName();
    }
    return name;
  }

  @Nullable
  public String getName() {
    return getAntFile().getName();
  }


  public AntBuildModelBase getModel() {
    return (AntBuildModelBase)myAntConfiguration.getModel(this);
  }

  @Nullable
  public AntBuildModelBase getModelIfRegistered() {
    return (AntBuildModelBase)myAntConfiguration.getModelIfRegistered(this);
  }

  public boolean isRunInBackground() {
    return RUN_IN_BACKGROUND.value(myAllOptions);
  }

  public PsiFile getAntFile() {
    return myFile;
  }

  public Project getProject() {
    return myFile.getProject();
  }

  @Nullable
  public VirtualFile getVirtualFile() {
    return myFile.getVirtualFile();
  }

  public AbstractProperty.AbstractPropertyContainer getAllOptions() {
    return myAllOptions;
  }

  @Nullable
  public String getPresentableUrl() {
    final VirtualFile file = getVirtualFile();
    return (file == null) ? null : file.getPresentableUrl();
  }

  public boolean shouldExpand() {
    boolean expandFirstTime = myExpandFirstTime;
    myExpandFirstTime = false;
    return expandFirstTime;
  }

  public boolean isTargetVisible(final AntBuildTarget target) {
    TargetFilter filter = findFilter(target.getName());
    if (filter == null) {
      return target.isDefault() || target.getNotEmptyDescription() != null;
    }
    return filter.isVisible();
  }

  public void updateProperties() {
    final Map<String, AntBuildTarget> targetByName =
      ContainerUtil.assignKeys(Arrays.asList(getModel().getTargets()).iterator(), new Convertor<AntBuildTarget, String>() {
        public String convert(AntBuildTarget target) {
          return target.getName();
        }
      });
    final ArrayList<TargetFilter> filters = TARGET_FILTERS.getModifiableList(getAllOptions());
    for (Iterator<TargetFilter> iterator = filters.iterator(); iterator.hasNext();) {
      TargetFilter filter = iterator.next();
      String name = filter.getTargetName();
      AntBuildTarget target = targetByName.get(name);
      if (target != null) {
        filter.updateDescription(target);
        targetByName.remove(name);
      }
      else {
        iterator.remove();
      }
    }
    for (AntBuildTarget target : targetByName.values()) {
      filters.add(TargetFilter.fromTarget(target));
    }
  }

  public void updateConfig() {
    basicUpdateConfig();
    DaemonCodeAnalyzer.getInstance(getProject()).restart();
  }

  public void setTreeView(final boolean value) {
    TREE_VIEW.primSet(myAllOptions, value);
  }

  public void setVerboseMode(final boolean value) {
    VERBOSE.primSet(myAllOptions, value);
  }

  public boolean isViewClosedWhenNoErrors() {
    return CLOSE_ON_NO_ERRORS.value(myAllOptions);
  }

  public void readWorkspaceProperties(final Element parentNode) throws InvalidDataException {
    myWorkspaceOptions.readExternal(parentNode);
  }

  public void writeWorkspaceProperties(final Element parentNode) throws WriteExternalException {
    myWorkspaceOptions.writeExternal(parentNode);
  }

  public void readProperties(final Element parentNode) throws InvalidDataException {
    myProjectOptions.readExternal(parentNode);
    basicUpdateConfig();
    readWorkspaceProperties(parentNode); // Compatibility with old Idea
    updateProperties();
  }

  public void writeProperties(final Element parentNode) throws WriteExternalException {
    myProjectOptions.writeExternal(parentNode);
  }

  private void basicUpdateConfig() {
    registerPropertiesInPsi();
    bindAnt();
    myClassloaderHolder.updateClasspath();
    myFile.clearCaches();
  }

  private void registerPropertiesInPsi() {
    final DataContext context = MapDataContext.singleData(DataConstants.PROJECT, getProject());
    final MacroManager macroManager = MacroManager.getInstance();
    Iterator<BuildFileProperty> properties = ANT_PROPERTIES.getIterator(myAllOptions);
    while (properties.hasNext()) {
      BuildFileProperty property = properties.next();
      try {
        String value = property.getPropertyValue();
        value = macroManager.expandSilentMarcos(value, true, context);
        value = macroManager.expandSilentMarcos(value, false, context);
        myFile.setProperty(property.getPropertyName(), value);
      }
      catch (Macro.ExecutionCancelledException e) {
        LOG.debug(e);
      }
    }
  }

  private void bindAnt() {
    ANT_REFERENCE.set(getAllOptions(), ANT_REFERENCE.get(getAllOptions()).bind(GlobalAntConfiguration.getInstance()));
  }

  @Nullable
  private TargetFilter findFilter(final String targetName) {
    final List<TargetFilter> targetFilters = TARGET_FILTERS.get(myAllOptions);
    for (TargetFilter targetFilter : targetFilters) {
      if (Comparing.equal(targetName, targetFilter.getTargetName())) {
        return targetFilter;
      }
    }
    return null;
  }

  @NotNull
  public AntClassLoader getClassLoader() {
    return myClassloaderHolder.getClassloader();
  }
}
