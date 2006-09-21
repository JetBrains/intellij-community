package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.PluginId;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Mar 27, 2003
 * Time: 10:07:29 PM
 * To change this template use Options | File Templates.
 */
public class PluginNode implements IdeaPluginDescriptor {
  public static final int STATUS_UNKNOWN = 0;
  public static final int STATUS_INSTALLED = 1;
  public static final int STATUS_MISSING = 2;
  public static final int STATUS_CURRENT = 3;
  public static final int STATUS_NEWEST = 4;
  public static final int STATUS_DOWNLOADED = 5;
  public static final int STATUS_DELETED = 6;

  private PluginId id;
  private String name;
  private String version;
  private String vendor;
  private String description;
  private String sinceBuild;
  private String changeNotes;
  private String downloads;
  private String category;
  private String size;
  private String vendorEmail;
  private String vendorUrl;
  private String url;
  private long date = Long.MAX_VALUE;
  private List<PluginId> depends;

  private int status = STATUS_UNKNOWN;
  private boolean loaded = false;

  public PluginNode() {
  }

  public PluginNode(PluginId id) {
    this.id = id;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    if (id == null) {
      id = PluginId.getId(name);
    }
    this.name = name;
  }

  public void setId(String id) {
    this.id = PluginId.getId(id);
  }

  public String getCategory() {
    return category;
  }

  /**
   * Be carefull when comparing Plugins versions. Use
   * PluginManagerColumnInfo.compareVersion() for version comparing.
   *
   * @return Return plugin version
   */
  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getVendor() {
    return vendor;
  }

  public void setVendor(String vendor) {
    this.vendor = vendor;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getChangeNotes() {
    return changeNotes;
  }

  public void setChangeNotes(String changeNotes) {
    this.changeNotes = changeNotes;
  }

  public String getSinceBuild() {
    return sinceBuild;
  }

  public void setSinceBuild(String sinceBuild) {
    this.sinceBuild = sinceBuild;
  }

  /**
   * In complex environment use PluginManagerColumnInfo.getRealNodeState () method instead.
   *
   * @return Status of plugin
   */
  public int getStatus() {
    return status;
  }

  public void setStatus(int status) {
    this.status = status;
  }

  public String toString() {
    return getName();
  }

  public boolean isLoaded() {
    return loaded;
  }

  public void setLoaded(boolean loaded) {
    this.loaded = loaded;
  }

  public String getDownloads() {
    return downloads;
  }

  public void setDownloads(String downloads) {
    this.downloads = downloads;
  }

  public String getSize() {
    return size;
  }

  public void setSize(String size) {
    this.size = size;
  }

  public String getVendorEmail() {
    return vendorEmail;
  }

  public void setVendorEmail(String vendorEmail) {
    this.vendorEmail = vendorEmail;
  }

  public String getVendorUrl() {
    return vendorUrl;
  }

  public void setVendorUrl(String vendorUrl) {
    this.vendorUrl = vendorUrl;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public void setDate(String date) {
    this.date = Long.valueOf(date).longValue();
  }

  public long getDate() {
    return date;
  }

  public int hashCode() {
    return name.hashCode();
  }

  public boolean equals(Object object) {
    return object instanceof PluginNode && name.equals(((PluginNode)object).getName());
  }

  public List<PluginId> getDepends() {
    return depends;
  }

  public void setDepends(List<PluginId> depends) {
    this.depends = depends;
  }


  public void addDepends(PluginId depends) {
    if (this.depends == null) {
      this.depends = new ArrayList<PluginId>();
    }

    this.depends.add(depends);
  }

  /**
   * Methods below implement PluginDescriptor and IdeaPluginDescriptor interface
   */
  public PluginId getPluginId() {
    return id;
  }

  @Nullable
  public ClassLoader getPluginClassLoader() {
    return null;
  }

  @Nullable
  public File getPath() {
    return null;
  }

  @Nullable
  public PluginId[] getDependentPluginIds() {
    return null;
  }

  @Nullable
  public PluginId[] getOptionalDependentPluginIds() {
    return null;
  }

  @Nullable
  public String getResourceBundleBaseName() {
    return null;
  }

  @Nullable
  public Element getActionsDescriptionElement() {
    return null;
  }

  @Nullable
  public Element getAppComponents() {
    return null;
  }

  @Nullable
  public Element getProjectComponents() {
    return null;
  }

  @Nullable
  public Element getModuleComponents() {
    return null;
  }

  @Nullable
  public HelpSetPath[] getHelpSets() {
    return null;
  }

  @Nullable
  public String getVendorLogoPath() {
    return null;
  }

  public boolean getUseIdeaClassLoader() {
    return false;
  }
}
