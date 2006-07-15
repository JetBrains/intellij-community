package com.intellij.ide.plugins;

import com.intellij.CommonBundle;
import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * @author mike
 */
public class IdeaPluginDescriptorImpl implements JDOMExternalizable, IdeaPluginDescriptor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.plugins.PluginDescriptor");

  public static final IdeaPluginDescriptorImpl[] EMPTY_ARRAY = new IdeaPluginDescriptorImpl[0];
  private String myName;
  private PluginId myId;
  private String myDescription;
  private String myResourceBundleBaseName;
  private String myChangeNotes;
  private String myVersion;
  private String myVendor;
  private String myVendorEmail;
  private String myVendorUrl;
  private String myVendorLogoPath;
  private String myCategory;
  private String url;
  private File myPath;
  private PluginId[] myDependencies;
  private PluginId[] myOptionalDependencies;
  private Element myActionsElement = null;
  private Element myAppComponents = null;
  private Element myProjectComponents = null;
  private Element myModuleComponents = null;
  private boolean myDeleted = false;
  private ClassLoader myLoader;
  private HelpSetPath[] myHelpSets;
  private int myFormatVersion = 1;
  private List<Element> myExtensions;
  private List<Element> myExtensionsPoints;
  private String myDescriptionChildText;
  private String myDownloadCounter;
  private long myDate;
  private boolean myUseIdeaClassLoader;

  @NonNls private static final String ATTRIBUTE_URL = "url";
  @NonNls private static final String ELEMENT_NAME = "name";
  @NonNls private static final String ELEMENT_ID = "id";
  @NonNls private static final String ATTRIBUTE_VERSION = "version";
  @NonNls private static final String ATTRIBUTE_USE_IDEA_CLASSLOADER = "use-idea-classloader";
  @NonNls private static final String ELEMENT_RESOURCE_BUNDLE = "resource-bundle";
  @NonNls private static final String ELEMENT_DESCRIPTION = "description";
  @NonNls private static final String ELEMENT_CHANGE_NOTES = "change-notes";
  @NonNls private static final String ELEMENT_VENDOR = "vendor";
  @NonNls private static final String ELEMENT_CATEGORY = "category";
  @NonNls private static final String ELEMENT_DEPENDS = "depends";
  @NonNls private static final String ELEMENT_HELPSET = "helpset";
  @NonNls private static final String ATTRIBUTE_FILE = "file";
  @NonNls private static final String ATTRIBUTE_PATH = "path";
  @NonNls private static final String ATTRIBUTE_EMAIL = "email";
  @NonNls private static final String ATTRIBUTE_LOGO = "logo";
  @NonNls private static final String ELEMENT_APPLICATION_COMPONENTS = "application-components";
  @NonNls private static final String ELEMENT_PROJECT_COMPONENTS = "project-components";
  @NonNls private static final String ELEMENT_MODULE_COMPONENTS = "module-components";
  @NonNls private static final String ELEMENT_ACTIONS = "actions";

  @NonNls private static final String ELEMENT_EXTENSIONS = "extensions";
  @NonNls private static final String ELEMENT_EXTENSION_POINTS = "extensionPoints";
  @NonNls private static final String ATT_OPTIONAL = "optional";

  public IdeaPluginDescriptorImpl(File pluginPath) {
    myPath = pluginPath;
  }

  public void setPath(File path) {
    myPath = path;
  }

  public File getPath() {
    return myPath;
  }

  public void readExternal(Element element) throws InvalidDataException {
    url = element.getAttributeValue(ATTRIBUTE_URL);
    myName = element.getChildText(ELEMENT_NAME);
    String idString = element.getChildText(ELEMENT_ID);
    if (idString == null || idString.length() == 0) {
      idString = myName;
    }
    myId = PluginId.getId(idString);

    String internalVersionString = element.getAttributeValue(ATTRIBUTE_VERSION);
    if (internalVersionString != null) {
      try {
        myFormatVersion = Integer.parseInt(internalVersionString);
      }
      catch (NumberFormatException e) {
        LOG.error(new PluginException("Invalid value in plugin.xml format version: " + internalVersionString, e, myId));
      }
    }
    myUseIdeaClassLoader = element.getAttributeValue(ATTRIBUTE_USE_IDEA_CLASSLOADER) != null &&
                           Boolean.parseBoolean(element.getAttributeValue(ATTRIBUTE_USE_IDEA_CLASSLOADER));

    myResourceBundleBaseName = element.getChildText(ELEMENT_RESOURCE_BUNDLE);

    myDescriptionChildText = element.getChildText(ELEMENT_DESCRIPTION);
    myChangeNotes = element.getChildText(ELEMENT_CHANGE_NOTES);
    myVersion = element.getChildText(ATTRIBUTE_VERSION);
    myVendor = element.getChildText(ELEMENT_VENDOR);
    myCategory = element.getChildText(ELEMENT_CATEGORY);


    List children = element.getChildren(ELEMENT_DEPENDS);
    Set<PluginId> dependentPlugins = new HashSet<PluginId>(children.size());
    Set<PluginId> optionalDependentPlugins = new HashSet<PluginId>(children.size());
    for (final Object aChildren : children) {
      final Element dependentPlugin = (Element)aChildren;
      String text = dependentPlugin.getText();
      if (text != null && text.length() > 0) {
        final PluginId id = PluginId.getId(text);
        dependentPlugins.add(id);
        if (Boolean.valueOf(dependentPlugin.getAttributeValue(ATT_OPTIONAL)).booleanValue()) {
          optionalDependentPlugins.add(id);
        }
      }
    }
    myDependencies = dependentPlugins.toArray(new PluginId[dependentPlugins.size()]);
    myOptionalDependencies = optionalDependentPlugins.toArray(new PluginId[optionalDependentPlugins.size()]);

    children = element.getChildren(ELEMENT_HELPSET);
    List<HelpSetPath> hsPathes = new ArrayList<HelpSetPath>(children.size());
    for (final Object aChildren1 : children) {
      final Element helpset = (Element)aChildren1;
      HelpSetPath hsPath = new HelpSetPath(helpset.getAttributeValue(ATTRIBUTE_FILE), helpset.getAttributeValue(ATTRIBUTE_PATH));
      hsPathes.add(hsPath);
    }
    myHelpSets = hsPathes.toArray(new HelpSetPath[hsPathes.size()]);

    Element vendor = element.getChild(ELEMENT_VENDOR);
    if (vendor != null) {
      myVendorEmail = element.getChild(ELEMENT_VENDOR).getAttributeValue(ATTRIBUTE_EMAIL);
      myVendorUrl = element.getChild(ELEMENT_VENDOR).getAttributeValue(ATTRIBUTE_URL);
      myVendorLogoPath = element.getChild(ELEMENT_VENDOR).getAttributeValue(ATTRIBUTE_LOGO);
    }

    myAppComponents = element.getChild(ELEMENT_APPLICATION_COMPONENTS);
    myProjectComponents = element.getChild(ELEMENT_PROJECT_COMPONENTS);
    myModuleComponents = element.getChild(ELEMENT_MODULE_COMPONENTS);

    final List<Element> extensionsRoots = JDOMUtil.getChildrenFromAllNamespaces(element, ELEMENT_EXTENSIONS);
    if (!extensionsRoots.isEmpty()) {
      myExtensions = new ArrayList<Element>();
      for (Element extensionsRoot : extensionsRoots) {
        for (final Object o : extensionsRoot.getChildren()) {
          myExtensions.add((Element)o);
        }
      }
    }

    final List<Element> extensionPointRoots = JDOMUtil.getChildrenFromAllNamespaces(element, ELEMENT_EXTENSION_POINTS);
    if (!extensionPointRoots.isEmpty()) {
      myExtensionsPoints = new ArrayList<Element>();
      for (Element root : extensionPointRoots) {
        for (Object o : root.getChildren()) {
          myExtensionsPoints.add((Element)o);
        }
      }
    }

    myActionsElement = element.getChild(ELEMENT_ACTIONS);
  }

  private static String loadDescription(final String descriptionChildText, @Nullable final ResourceBundle bundle, final PluginId id) {
    if (bundle == null) {
      return descriptionChildText;
    }

    return CommonBundle.messageOrDefault(bundle, createDescriptionKey(id), descriptionChildText);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static String createDescriptionKey(final PluginId id) {
    return "plugin." + id + ".description";
  }

  void registerExtensions() {
    if (myExtensions != null || myExtensionsPoints != null) {
      Extensions.getRootArea().getExtensionPoint(Extensions.AREA_LISTENER_EXTENSION_POINT).registerExtension(new AreaListener() {
        public void areaCreated(String areaClass, AreaInstance areaInstance) {
          final ExtensionsArea area = Extensions.getArea(areaInstance);
          area.registerAreaExtensionsAndPoints(IdeaPluginDescriptorImpl.this, myExtensionsPoints, myExtensions);
        }

        public void areaDisposing(String areaClass, AreaInstance areaInstance) {
        }
      });
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    throw new WriteExternalException("Not supported");
  }

  public int getFormatVersion() {
    return myFormatVersion;
  }

  public String getDescription() {
    return myDescription;
  }

  public String getChangeNotes() {
    return myChangeNotes;
  }

  public String getName() {
    return myName;
  }

  public PluginId[] getDependentPluginIds() {
    return myDependencies;
  }

  public PluginId[] getOptionalDependentPluginIds() {
    return myOptionalDependencies;
  }

  public String getVendor() {
    return myVendor;
  }

  public String getVersion() {
    return myVersion;
  }

  public String getResourceBundleBaseName() {
    return myResourceBundleBaseName;
  }

  public String getCategory() {
    return myCategory;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public List<File> getClassPath() {
    if (myPath.isDirectory()) {
      final List<File> result = new ArrayList<File>();
      final File classesDir = new File(myPath, "classes");

      if (classesDir.exists()) {
        result.add(classesDir);
      }

      final File[] files = new File(myPath, "lib").listFiles();
      if (files != null && files.length > 0) {
        for (final File f : files) {
          if (f.isFile()) {
            final String name = f.getName().toLowerCase();
            if (name.endsWith(".jar") || name.endsWith(".zip")) {
              result.add(f);
            }
          }
          else {
            result.add(f);
          }
        }
      }

      return result;
    }
    else {
      return Collections.singletonList(myPath);
    }
  }

  public Element getActionsDescriptionElement() {
    return myActionsElement;
  }

  public Element getAppComponents() {
    return myAppComponents;
  }

  public Element getProjectComponents() {
    return myProjectComponents;
  }

  public Element getModuleComponents() {
    return myModuleComponents;
  }

  public String getVendorEmail() {
    return myVendorEmail;
  }

  public String getVendorUrl() {
    return myVendorUrl;
  }

  public String getUrl() {
    return url;
  }

  @NonNls
  public String toString() {
    return "PluginDescriptor[name='" + myName + "', classpath='" + myPath + "']";
  }

  public boolean isDeleted() {
    return myDeleted;
  }

  public void setDeleted(boolean deleted) {
    myDeleted = deleted;
  }

  public void setLoader(ClassLoader loader) {
    myLoader = loader;

    //Now we're ready to load root area extensions

    Extensions.getRootArea().registerAreaExtensionsAndPoints(this, myExtensionsPoints, myExtensions);

    initialize(getPluginClassLoader());
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof IdeaPluginDescriptorImpl)) return false;

    final IdeaPluginDescriptorImpl pluginDescriptor = (IdeaPluginDescriptorImpl)o;

    if (myName != null ? !myName.equals(pluginDescriptor.myName) : pluginDescriptor.myName != null) return false;

    return true;
  }

  public int hashCode() {
    return (myName != null ? myName.hashCode() : 0);
  }

  public HelpSetPath[] getHelpSets() {
    return myHelpSets;
  }

  public PluginId getPluginId() {
    return myId;
  }

  /*
     This setter was explicitly defined to be able to set a category for a
     descriptor outside its loading from the xml file.
     Problem was that most commonly plugin authors do not publish the plugin's
     category in its .xml file so to be consistent in plugins representation
     (e.g. in the Plugins form) we have to set this value outside.
  */
  public void setCategory( String category ){
    myCategory = category;
  }

  /*
     This setter was explicitly defined to be able to set downloads count for a
     descriptor outside its loading from the xml file since this information
     is available only from the site.
  */
  public void setDownloadsCount( String dwnlds ){
    myDownloadCounter = dwnlds;
  }

  public String getDownloads(){
    return myDownloadCounter;
  }

  /*
     This setter was explicitly defined to be able to set date for a
     descriptor outside its loading from the xml file since this information
     is available only from the site.
  */
  public void setDate( long date ){
    myDate = date;
  }

  public long getDate(){
    return myDate;
  }

  public void setVendor( final String val )
  {
    myVendor = val;
  }
  public void setVendorEmail( final String val )
  {
    myVendorEmail = val;
  }
  public void setVendorUrl( final String val )
  {
    myVendorUrl = val;
  }
  public void setUrl( final String val )
  {
    url = val;
  }

  public ClassLoader getPluginClassLoader() {
    return myLoader != null ? myLoader : getClass().getClassLoader();
  }

  public String getVendorLogoPath() {
    return myVendorLogoPath;
  }

  public boolean getUseIdeaClassLoader() {
    return myUseIdeaClassLoader;
  }

  public void setVendorLogoPath(final String vendorLogoPath) {
    myVendorLogoPath = vendorLogoPath;
  }

  public void initialize(@NotNull ClassLoader classLoader) {
    ResourceBundle bundle = null;
    if (myResourceBundleBaseName != null) {
      try {
        bundle = ResourceBundle.getBundle(myResourceBundleBaseName, Locale.getDefault(), classLoader);
      }
      catch (MissingResourceException e) {
        LOG.info("Cannot find plugin " + myId + " resource-bundle: " + myResourceBundleBaseName);
      }
    }

    myDescription = loadDescription(myDescriptionChildText, bundle, myId);
  }

}
