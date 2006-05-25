package com.intellij.ide.plugins;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.SortableColumnModel;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Dec 11, 2003
 * Time: 2:55:50 PM
 * To change this template use Options | File Templates.
 */
class PluginManagerColumnInfo extends ColumnInfo<IdeaPluginDescriptor, String> {
  public static final int COLUMN_STATUS = 0;
  public static final int COLUMN_NAME = 1;
  public static final int COLUMN_DOWNLOADS = 2;
  public static final int COLUMN_DATE = 3;
  public static final int COLUMN_CATEGORY = 4;
  public static final int COLUMN_INSTALLED_VERSION = 5;
  public static final int COLUMN_SIZE = 6;
  public static final int COLUMN_VERSION = 7;
  public static final int COLUMN_STATE = 8;
  private static final float mgByte = 1024.0f * 1024.0f;
  private static final float kByte = 1024.0f;

  private static final Color RED_COLOR = new Color (255, 231, 227);
  private static final Color GREEN_COLOR = new Color (232, 243, 221);
  private static final Color LIGHT_BLUE_COLOR = new Color (212, 222, 255);
  private static final Color HOTVERSION = new Color (255, 200, 170);

  private static Date yearAgo;
  private static Date weekAgo;

  public static final String [] COLUMNS = {
    // this is a fake naming just for simplifying code for rendering table header
    "I",
    IdeBundle.message("column.plugins.name"),
    IdeBundle.message("column.plugins.downloads"),
    IdeBundle.message("column.plugins.date"),
    IdeBundle.message("column.plugins.category")
  };

  private int columnIdx;
  private SortableProvider mySortableProvider;

  public PluginManagerColumnInfo(int columnIdx, SortableProvider sortableProvider) {
    super(COLUMNS [columnIdx]);
    this.columnIdx = columnIdx;
    mySortableProvider = sortableProvider;
  }

  public String valueOf(IdeaPluginDescriptor base)
  {
    if( columnIdx == COLUMN_NAME )
      return base.getName();
    else
    if( columnIdx == COLUMN_DOWNLOADS )
    {
      //  Base class IdeaPluginDescriptor does not declare this field.
      return (base instanceof PluginNode) ? ((PluginNode)base).getDownloads() :
                                         ((IdeaPluginDescriptorImpl)base).getDownloads();
    }
    if( columnIdx == COLUMN_DATE )
    {
      //  Base class IdeaPluginDescriptor does not declare this field.
      long date = (base instanceof PluginNode) ? ((PluginNode)base).getDate() :
                                              ((IdeaPluginDescriptorImpl)base).getDate();
      if( date != 0 )
        return DateFormat.getDateInstance(DateFormat.MEDIUM).format( new Date( date ));
      else
        return IdeBundle.message("plugin.info.not.available");
    }
    else
    if( columnIdx == COLUMN_CATEGORY )
      return base.getCategory();
    else
      // For COLUMN_STATUS - set of icons show the actual state of installed plugins.
      return "";
  }

  public Comparator<IdeaPluginDescriptor> getComparator()
  {
    final boolean sortDirection = (mySortableProvider.getSortOrder() == SortableColumnModel.SORT_ASCENDING);

    switch (columnIdx)
    {
      case COLUMN_STATUS:
        //  Return GetRealNodeEstate as is for availabe plugins and "State" for installed ones.
        return new Comparator<IdeaPluginDescriptor>() {
          public int compare(IdeaPluginDescriptor o1, IdeaPluginDescriptor o2) {
            if (o1 instanceof PluginNode && o2 instanceof IdeaPluginDescriptorImpl) {
              return sortDirection ? -1 : 1;
            }
            else
            if (o2 instanceof PluginNode && o1 instanceof IdeaPluginDescriptorImpl) {
              return sortDirection ? 1 : -1;
            }
            else
            if (o1 instanceof PluginNode && o2 instanceof PluginNode) {
              PluginNode p1 = (PluginNode)(sortDirection ? o1 : o2);
              PluginNode p2 = (PluginNode)(sortDirection ? o2 : o1);

              int status1 = getRealNodeState( p1 );
              int status2 = getRealNodeState( p2 );
              if( status1 == status2 )
                return 0;
              else
                return ( status1 > status2 ) ? 1 : -1;
            }
            else
            {
                IdeaPluginDescriptorImpl p1 = (IdeaPluginDescriptorImpl)(sortDirection ? o1 : o2);
                IdeaPluginDescriptorImpl p2 = (IdeaPluginDescriptorImpl)(sortDirection ? o2 : o1);

                if( p1.isDeleted() && ! p2.isDeleted() )
                  return -1;
                else if ( !p1.isDeleted() && p2.isDeleted() )
                  return 1;
                else if( PluginsTableModel.hasNewerVersion( p1.getPluginId() ) &&
                         !PluginsTableModel.hasNewerVersion( p2.getPluginId() ))
                  return 1;
                else if( !PluginsTableModel.hasNewerVersion( p1.getPluginId() ) &&
                         PluginsTableModel.hasNewerVersion( p2.getPluginId() ))
                  return -1;
            }
            return 0;
          }
        };

      case COLUMN_NAME:
        return new Comparator<IdeaPluginDescriptor>() {
          public int compare(IdeaPluginDescriptor o1, IdeaPluginDescriptor o2)
          {
              String name1 = (sortDirection ? o1 : o2).getName();
              String name2 = (sortDirection ? o2 : o1).getName();
              return compareStrings( name1, name2 );
          }
        };

      case COLUMN_DOWNLOADS:
        return new Comparator<IdeaPluginDescriptor>() {
          public int compare(IdeaPluginDescriptor o1, IdeaPluginDescriptor o2) {
            if( !sortDirection )
            {
              IdeaPluginDescriptor swap = o2; o2 = o1; o1 = swap;
            }
            String count1 = (o1 instanceof PluginNode) ? ((PluginNode)o1).getDownloads() :
                                                         ((IdeaPluginDescriptorImpl)o1).getDownloads();
            String count2 = (o2 instanceof PluginNode) ? ((PluginNode)o2).getDownloads() :
                                                         ((IdeaPluginDescriptorImpl)o2).getDownloads();
            if( count1 != null && count2 != null )
              return new Long( count1 ).compareTo( new Long ( count2 ));
            else
            if( count1 != null )
              return 1;
            else
              return -1;
          }
        };

        case COLUMN_CATEGORY:
          return new Comparator<IdeaPluginDescriptor>() {
            public int compare(IdeaPluginDescriptor o1, IdeaPluginDescriptor o2) {
                String cat1 = (sortDirection ? o1 : o2).getCategory();
                String cat2 = (sortDirection ? o2 : o1).getCategory();
                return compareStrings( cat1, cat2 );
            }
          };

      case COLUMN_DATE:
        return new Comparator<IdeaPluginDescriptor>() {
          public int compare(IdeaPluginDescriptor o1, IdeaPluginDescriptor o2) {
            if( !sortDirection )
            {
              IdeaPluginDescriptor swap = o2; o2 = o1; o1 = swap;
            }
            long date1 = (o1 instanceof PluginNode) ? ((PluginNode)o1).getDate() :
                                                      ((IdeaPluginDescriptorImpl)o1).getDate();
            long date2 = (o2 instanceof PluginNode) ? ((PluginNode)o2).getDate() :
                                                      ((IdeaPluginDescriptorImpl)o2).getDate();
            if( date1 > date2 )
              return 1;
            else if( date1 < date2 )
              return -1;
            return 0;
          }
        };

      default:
        return new Comparator<IdeaPluginDescriptor> () {
          public int compare(IdeaPluginDescriptor o, IdeaPluginDescriptor o1) {
            return 0;
          }
        };
    }
  }

    public static int compareVersion (String v1, String v2) {
      if (v1 == null && v2 == null)
        return 0;
      else if( v1 == null )
        return -1;
      else if( v2 == null )
        return 1;

      String [] part1 = v1.split("\\.");
      String [] part2 = v2.split("\\.");

      int idx = 0;
      for (; idx < part1.length && idx < part2.length; idx++) {
        String p1 = part1[idx];
        String p2 = part2[idx];

        int cmp;
        //noinspection HardCodedStringLiteral
        if (p1.matches("\\d+") && p2.matches("\\d+")) {
          cmp = new Integer(p1).compareTo(new Integer(p2));
        } else {
          cmp = part1 [idx].compareTo(part2[idx]);
        }
        if (cmp != 0)
          return cmp;
      }

      if (part1.length == part2.length)
        return 0;
      else if (part1.length > idx)
        return 1;
      else
        return -1;
    }

    public static int compareStrings (String str1, String str2) {
      if (str1 == null && str2 == null)
        return 0;
      else if (str1 == null )
        return -1;
      else if ( str2 == null)
        return 1;
       else
          return str1.compareToIgnoreCase( str2 );
    }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static String getFormattedSize( String size )
  {
    if( size.equals( "-1" )){
      return IdeBundle.message( "plugin.info.unknown" );
    }
    else
    if( size.length() >= 4 )
    {
        if( size.length() < 7 )
        {
            size = String.format("%.1f", (float)Integer.parseInt(size) / kByte) + " K";
        }
        else
        {
            size = String.format("%.1f", (float)Integer.parseInt(size) / mgByte ) + " M";
        }
    }
    return size;
  }

    public static int getRealNodeState( PluginNode node )
    {
        SetDateAnchors();

        if (node.getStatus() == PluginNode.STATUS_DOWNLOADED)
          return PluginNode.STATUS_DOWNLOADED;

        Date pluginDate = new Date( node.getDate() );

        if( weekAgo.before( pluginDate ) )
            return PluginNode.STATUS_NEWEST;

        return PluginNode.STATUS_MISSING;
    }

  public TableCellRenderer getRenderer(IdeaPluginDescriptor o)
  {
      return new PluginTableCellRenderer();
  }

    public Class getColumnClass()
    {
        if( columnIdx == COLUMN_SIZE || columnIdx == COLUMN_DOWNLOADS )
            return Integer.class;
        else
            return String.class;
    }

    private static class PluginTableCellRenderer extends DefaultTableCellRenderer
    {
      public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                     boolean hasFocus, int row, int column)
      {
        Object descriptor = ((PluginTable)table).getObjectAt(row);
        if (column == 0)
        {
          setHorizontalAlignment( SwingConstants.CENTER );
          if( descriptor instanceof IdeaPluginDescriptorImpl )
          {
              if( PluginsTableModel.hasNewerVersion(((IdeaPluginDescriptorImpl)descriptor).getPluginId() ))
                setIcon(IconLoader.getIcon("/nodes/pluginobsolete.png"));
              else
                setIcon(IconLoader.getIcon("/nodes/plugin.png"));
          }
          else
            setIcon(IconLoader.getIcon("/nodes/pluginnotinstalled.png"));
        }

        SetDateAnchors();
        if( !isSelected )
        {
              if (column == 2) {
                setHorizontalAlignment( SwingConstants.RIGHT );
              }
              setForeground( Color.black );

              if( descriptor instanceof IdeaPluginDescriptorImpl )
              {
                setEnabled( !((IdeaPluginDescriptorImpl)descriptor).isDeleted() );

                if(((IdeaPluginDescriptorImpl)descriptor).isDeleted())
                  setForeground( Color.lightGray );
                else
                  setBackground( LIGHT_BLUE_COLOR );
              }
              else
              {
                  PluginNode node = (PluginNode)descriptor;
                  Date pluginDate = new Date( node.getDate() );

                  setEnabled( true );
                  switch (PluginManagerColumnInfo.getRealNodeState( node ))
                  {
                    case PluginNode.STATUS_OUT_OF_DATE:
                      setBackground( RED_COLOR );
                      break;
                    case PluginNode.STATUS_CURRENT:
                      setBackground( GREEN_COLOR );
                      break;
                    default:
                      setBackground( Color.white );
                  }

                  if( yearAgo.after( pluginDate ) )
                      setForeground( Color.lightGray );
                  else
                  if( weekAgo.before( pluginDate ) )
                      setBackground( HOTVERSION );
                  else
                      setBackground( Color.white );
              }
        }

        return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      }
    }

    private static void  SetDateAnchors()
    {
        if( yearAgo == null )
        {
            Calendar current = Calendar.getInstance();
            current.add( Calendar.DAY_OF_MONTH, -365 );
            yearAgo = current.getTime();

            current = Calendar.getInstance();
            current.add( Calendar.DAY_OF_MONTH, -30 );
            weekAgo = current.getTime();
        }
    }

    @Override
    public int getWidth( JTable table )
    {
        return ( columnIdx == 0 ) ? 35 : -1;
    }
}
