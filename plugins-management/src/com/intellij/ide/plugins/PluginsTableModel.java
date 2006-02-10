package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.PluginId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Dec 26, 2003
 * Time: 3:51:58 PM
 * To change this template use Options | File Templates.
 */
public class PluginsTableModel extends PluginTableModel<IdeaPluginDescriptor>
{
    public static Hashtable<PluginId, Object> NewVersions2Plugins = new Hashtable<PluginId, Object>();
    private static Hashtable<PluginId, String> UpdateVersions = new Hashtable<PluginId, String>();

    public PluginsTableModel(SortableProvider sortableProvider)
    {
        super(new PluginManagerColumnInfo [] {
            new PluginManagerColumnInfo(PluginManagerColumnInfo.COLUMN_STATUS, sortableProvider),
            new PluginManagerColumnInfo(PluginManagerColumnInfo.COLUMN_NAME, sortableProvider),
            new PluginManagerColumnInfo(PluginManagerColumnInfo.COLUMN_DOWNLOADS, sortableProvider),
            new PluginManagerColumnInfo(PluginManagerColumnInfo.COLUMN_DATE, sortableProvider),
            new PluginManagerColumnInfo(PluginManagerColumnInfo.COLUMN_CATEGORY, sortableProvider)
        }, sortableProvider);

        view = new ArrayList<IdeaPluginDescriptor>(Arrays.asList(PluginManager.getPlugins()));
        sortByColumn(sortableProvider.getSortColumn());
    }

    public void addData( ArrayList<IdeaPluginDescriptor> list )
    {
        //  For each downloadable plugin we need to know whether its counterpart
        //  is already installed, and if yes compare the difference in versions:
        //  availability of newer versions will be indicated separately.
        for( IdeaPluginDescriptor descr : list )
        {
            IdeaPluginDescriptor existing = PluginManager.getPlugin( descr.getPluginId());
            if( existing == null )
            {
              view.add( descr );
              UpdateVersions.put( descr.getPluginId(), descr.getVersion() );
            }
            else
            {
              updateExistingPluginInfo( (PluginNode)descr, existing );
            }
        }
        sortByColumn(sortableProvider.getSortColumn());
    }

    public void  modifyData( ArrayList<IdeaPluginDescriptor> list )
    {
        //  For each downloadable plugin we need to know whether its counterpart
        //  is already installed, and if yes compare the difference in versions:
        //  availability of newer versions will be indicated separately.
        for( IdeaPluginDescriptor descr : list )
        {
            PluginId descrId = descr.getPluginId();
            IdeaPluginDescriptor existing = PluginManager.getPlugin( descrId );
            if( existing == null )
            {
                if( UpdateVersions.containsKey( descrId ) )
                {
                    String currVersion = UpdateVersions.get( descrId );
                    int state = PluginManagerColumnInfo.compareVersion( descr.getVersion(), currVersion );
                    if( state > 0 )
                    {
                        for( int i = 0; i < view.size(); i++ )
                        {
                            IdeaPluginDescriptor obsolete = view.get( i );
                            if( obsolete.getPluginId() == descrId )
                                view.remove( obsolete );
                        }
                        view.add( descr );
                    }
                }
                else
                {
                    view.add( descr );
                }
            }
            else
            {
              updateExistingPluginInfo( (PluginNode) descr, existing );
            }
        }
        sortByColumn(sortableProvider.getSortColumn());
    }

  private static void  updateExistingPluginInfo( PluginNode descr, IdeaPluginDescriptor existing )
  {
    int state = PluginManagerColumnInfo.compareVersion( descr.getVersion(), existing.getVersion());
    if( state > 0 )
    {
        NewVersions2Plugins.put( existing.getPluginId(), 1 );
    }

    //  Almost all plugins do not set "category" tag in their "META-INF/plugin.xml"
    //  descriptors. So while we are going neither to modify all descriptors nor to
    //  insist that developers do that, we just set the category mentioned in
    //  overall list to the existin descriptor if it is not set yet.

    final String pluginCategory = descr.getCategory();
    final IdeaPluginDescriptorImpl plugin = (IdeaPluginDescriptorImpl)existing;
    if( pluginCategory != null )
    {
      plugin.setCategory( pluginCategory );
    }
    plugin.setDownloadsCount( descr.getDownloads() );
    plugin.setVendor( descr.getVendor() );
    plugin.setVendorEmail( descr.getVendorEmail() );
    plugin.setVendorUrl( descr.getVendorUrl() );
    plugin.setUrl( descr.getUrl() );
  }

  public static boolean hasNewerVersion( PluginId descr )
  {
    return NewVersions2Plugins.containsKey( descr );
  }
}
