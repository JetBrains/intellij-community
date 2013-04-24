/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.DataManager;
import com.intellij.ide.RecentProjectsManagerBase;
import com.intellij.ide.ReopenProjectAction;
import com.intellij.ide.dnd.FileCopyPasteUtil;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.plugins.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WelcomeScreen;
import com.intellij.ui.*;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;

import static java.awt.GridBagConstraints.*;

/**
 * @author pti
 * @author Konstantin Bulenkov
 */
public class DefaultWelcomeScreen implements WelcomeScreen {
  private static final Insets ACTION_GROUP_CAPTION_INSETS = new Insets(20, 30, 5, 0);
  private static final Insets PLUGINS_CAPTION_INSETS = new Insets(20, 25, 0, 0);
  private static final Insets ACTION_ICON_INSETS = new Insets(5, 20, 15, 0);
  private static final Insets ACTION_NAME_INSETS = new Insets(15, 5, 0, 0);
  private static final Insets ACTION_DESCRIPTION_INSETS = new Insets(7, 5, 0, 30);
  private static final Insets NO_INSETS = new Insets(0, 0, 0, 0);

  private static final int MAIN_GROUP = 0;
  private static final int PLUGIN_DSC_MAX_WIDTH = 260;
  private static final int PLUGIN_DSC_MAX_ROWS = 2;
  private static final int PLUGIN_NAME_MAX_WIDTH = 180;
  private static final int PLUGIN_NAME_MAX_ROWS = 2;
  private static final int MAX_TOOLTIP_WIDTH = 400;
  private static final int ACTION_BUTTON_PADDING = 5;

  private static final Dimension ACTION_BUTTON_SIZE = new Dimension(64, 48);

  @NonNls private static final String CAPTION_FONT_NAME = "Tahoma";
  private static final Font TEXT_FONT = new Font(CAPTION_FONT_NAME, Font.PLAIN, 11);
  private static final Font LINK_FONT = new Font(CAPTION_FONT_NAME, Font.BOLD, 12);
  private static final Font GROUP_CAPTION_FONT = new Font(CAPTION_FONT_NAME, Font.BOLD, 18);

  private static final Color WELCOME_PANEL_BACKGROUND = UIUtil.isUnderDarcula() ? UIUtil.getControlColor() : Color.WHITE;
  private static final Color MAIN_PANEL_BACKGROUND = WELCOME_PANEL_BACKGROUND;
  private static final Color PLUGINS_PANEL_BACKGROUND = UIUtil.isUnderDarcula() ? UIUtil.getControlColor() : Gray._248;
  private static final Color PLUGINS_PANEL_BORDER = UIUtil.isUnderDarcula() ? PLUGINS_PANEL_BACKGROUND : Gray._234;
  private static final Color CAPTION_COLOR = UIUtil.isUnderDarcula() ? DarculaColors.BLUE : new Color(47, 67, 96);
  public static final SimpleTextAttributes CAPTION_BOLD_UNDERLINE_ATTRIBUTES =
    new SimpleTextAttributes(SimpleTextAttributes.STYLE_UNDERLINE | SimpleTextAttributes.STYLE_BOLD, CAPTION_COLOR);
  public static final SimpleTextAttributes CAPTION_UNDERLINE_ATTRIBUTES =
    new SimpleTextAttributes(SimpleTextAttributes.STYLE_UNDERLINE, CAPTION_COLOR);
  private static final Color DISABLED_CAPTION_COLOR = UIUtil.getInactiveTextColor();
  private static final Color ACTION_BUTTON_COLOR = Gray._0.withAlpha(0);
  private static final Color BUTTON_POPPED_COLOR = UIUtil.isUnderDarcula() ? Gray.get(WELCOME_PANEL_BACKGROUND.getRed() + 10) : Gray._241;
  private static final Color BUTTON_PUSHED_COLOR = UIUtil.isUnderDarcula() ? Gray.get(WELCOME_PANEL_BACKGROUND.getRed() + 5) : Gray._228;

  @NonNls private static final String ___HTML_SUFFIX = "...</html>";
  @NonNls private static final String ESC_NEW_LINE = "\\n";

  private final JBPanel myWelcomePanel;
  private final JPanel myMainPanel;
  private final JPanel myPluginsPanel;

  private Icon myCaptionImage;
  private Icon myDeveloperSlogan;
  private Color myCaptionBackground = new Color(23, 52, 150);

  private MyActionButton myPressedButton = null;
  private int mySelectedRow = -1;
  private int mySelectedColumn = -1;
  private int mySelectedGroup = -1;
  private int myPluginsIdx = -1;

  private JComponent myRecentProjectsPanel;

  public JPanel getWelcomePanel() {
    return myWelcomePanel;
  }

  @Override
  public void setupFrame(JFrame frame) {
  }

  public DefaultWelcomeScreen(JComponent rootPane) {
    initApplicationSpecificImages();

    myWelcomePanel = new JBPanel(new GridBagLayout()) {
      @Override
      public Dimension getPreferredSize() {
        return new Dimension(1024, 768);
      }
    };

    myWelcomePanel.setBackgroundImage(IconLoader.getIcon("/frame_background.png"));
    String icon = ApplicationInfoEx.getInstanceEx().getEditorBackgroundImageUrl();
    if (icon != null) myWelcomePanel.setCenterImage(IconLoader.getIcon(icon));

    // Create caption pane
    JPanel topPanel = createCaptionPane();

    // Create Main Panel for Quick Start and Documentation
    myMainPanel = new WelcomeScrollablePanel(new GridLayout(1, 2));
    //myMainPanel.setBackground(MAIN_PANEL_BACKGROUND);
    setUpMainPanel(rootPane);
    JScrollPane mainScrollPane = scrollPane(myMainPanel, null);

    // Create Plugins Panel
    myPluginsPanel = new WelcomeScrollablePanel(new GridBagLayout());
    myPluginsPanel.setBackground(PLUGINS_PANEL_BACKGROUND);
    setUpPluginsPanel();
    JScrollPane pluginsScrollPane = scrollPane(myPluginsPanel, PLUGINS_PANEL_BORDER);

    // Create Welcome panel
    GridBagConstraints gBC;
    myWelcomePanel.setBackground(WELCOME_PANEL_BACKGROUND);
    gBC = new GridBagConstraints(0, 0, 2, 1, 1, 0, NORTHWEST, HORIZONTAL, new Insets(7, 7, 7, 7), 0, 0);
    myWelcomePanel.add(topPanel, gBC);
    gBC = new GridBagConstraints(0, 1, 1, 1, 0.7, 1, NORTHWEST, BOTH, new Insets(0, 7, 7, 7), 0, 0);
    myWelcomePanel.add(mainScrollPane, gBC);
    /*
    gBC = new GridBagConstraints(1, 1, 1, 1, 0.3, 1, NORTHWEST, BOTH, new Insets(0, 0, 7, 7), 0, 0);
    myWelcomePanel.add(pluginsScrollPane, gBC);
    */
  }

  @Override
  public void dispose() {
  }

  private void initApplicationSpecificImages() {
    if (myCaptionImage == null) {
      ApplicationInfoEx applicationInfoEx = ApplicationInfoEx.getInstanceEx();
      myCaptionImage = IconLoader.getIcon(applicationInfoEx.getWelcomeScreenCaptionUrl());
      myDeveloperSlogan = IconLoader.getIcon(applicationInfoEx.getWelcomeScreenDeveloperSloganUrl());

      myCaptionBackground = UIUtil.getColorAt(myCaptionImage, myCaptionImage.getIconWidth() - 1, myCaptionImage.getIconHeight() - 2);
    }
  }

  private JPanel createCaptionPane() {
    JPanel topPanel = new JPanel(new GridBagLayout()) {
      public void paint(Graphics g) {
        Icon welcome = myCaptionImage;
        welcome.paintIcon(null, g, 0, 0);
        g.setColor(myCaptionBackground);
        g.fillRect(welcome.getIconWidth(), 0, getWidth() - welcome.getIconWidth(), welcome.getIconHeight());
        super.paint(g);
      }
    };
    topPanel.setOpaque(false);
    topPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, myCaptionBackground));

    JPanel transparentTopPanel = new JPanel();
    transparentTopPanel.setOpaque(false);

    topPanel.add(transparentTopPanel, new GridBagConstraints(0, 0, 1, 1, 1, 0, CENTER, HORIZONTAL, NO_INSETS, 0, 0));
    topPanel.add(new JLabel(myDeveloperSlogan), new GridBagConstraints(1, 0, 1, 1, 0, 0, SOUTHWEST, NONE, new Insets(0, 0, 0, 10), 0, 0));

    return topPanel;
  }

  private void setUpMainPanel(JComponent rootPane) {
    final ActionManager actionManager = ActionManager.getInstance();

    // Create QuickStarts group of actions
    ActionGroupDescriptor quickStarts = new ActionGroupDescriptor(UIBundle.message("welcome.screen.quick.start.action.group.name"), 0);
    // Append plug-in actions to the end of the QuickStart list
    quickStarts.appendActionsFromGroup((DefaultActionGroup)actionManager.getAction(IdeActions.GROUP_WELCOME_SCREEN_QUICKSTART));
    final JPanel quickStartPanel = quickStarts.getPanel();
    quickStartPanel.setOpaque(false);

    AnAction[] recentProjectsActions = RecentProjectsManagerBase.getInstance().getRecentProjectsActions(false);
    if (recentProjectsActions.length > 0) {
      myRecentProjectsPanel = new JPanel(new GridBagLayout());
      myRecentProjectsPanel.setOpaque(false);
      setUpRecentProjectsPanel(rootPane, recentProjectsActions);
      quickStartPanel.add(myRecentProjectsPanel, new GridBagConstraints(0, quickStarts.getIdx() + 2, 2, 1, 1, 1, NORTHWEST, HORIZONTAL,
                                                                      new Insets(14, 24, 5, 0), 0, 0));
    }

    // Add empty panel at the end of the QuickStarts panel
    JPanel emptyPanel_2 = new JPanel();
    emptyPanel_2.setOpaque(false);
    //emptyPanel_2.setBackground(MAIN_PANEL_BACKGROUND);
    quickStartPanel.add(emptyPanel_2, new GridBagConstraints(0, quickStarts.getIdx() + 3, 2, 1, 1, 1, NORTHWEST, BOTH, NO_INSETS, 0, 0));

    // Create Documentation group of actions
    ActionGroupDescriptor docsGroup = new ActionGroupDescriptor(UIBundle.message("welcome.screen.documentation.action.group.name"), 1);
    // Append plug-in actions to the end of the QuickStart list
    docsGroup.appendActionsFromGroup((DefaultActionGroup)actionManager.getAction(IdeActions.GROUP_WELCOME_SCREEN_DOC));
    final JPanel docsPanel = docsGroup.getPanel();
    docsPanel.setOpaque(false);
    // Add empty panel at the end of the Documentation list
    JPanel emptyPanel_3 = new JPanel();
    emptyPanel_3.setOpaque(false);
    //emptyPanel_3.setBackground(MAIN_PANEL_BACKGROUND);
    docsPanel.add(emptyPanel_3, new GridBagConstraints(0, docsGroup.getIdx() + 2, 2, 1, 1, 1, NORTHWEST, BOTH, NO_INSETS, 0, 0));

    // Add QuickStarts and Docs to main panel
    myMainPanel.add(quickStartPanel);
    myMainPanel.add(docsPanel);

    // Accept dropping of project file/dir from file manager and open that project
    myWelcomePanel.setTransferHandler(new OpenProjectTransferHandler(myWelcomePanel));
  }

  private void setUpRecentProjectsPanel(final JComponent rootPane, final AnAction[] recentProjectsActions) {
    myRecentProjectsPanel.setBackground(MAIN_PANEL_BACKGROUND);

    JLabel caption = new JLabel("Recent Projects");
    caption.setFont(GROUP_CAPTION_FONT);
    caption.setForeground(CAPTION_COLOR);
    myRecentProjectsPanel.add(caption, new GridBagConstraints(0, 0, 2, 1, 1, 0, NORTHWEST, HORIZONTAL, new Insets(0, 0, 20, 0), 0, 0));

    JLabel iconLabel = new JLabel();
    myRecentProjectsPanel.add(iconLabel, new GridBagConstraints(0, 1, 1, 5, 0, 0, NORTHWEST, NONE, new Insets(5, 0, 15, 20), 0, 0));

    int row = 1;
    for (final AnAction action : recentProjectsActions) {
      if (!(action instanceof ReopenProjectAction)) continue;

      final SimpleColoredComponent actionLabel = new SimpleColoredComponent() {
        @Override
        public Dimension getPreferredSize() {
          boolean hasIcon = getIcon() != null;
          Dimension preferredSize = super.getPreferredSize();
          return new Dimension(preferredSize.width + (hasIcon ? 0 : AllIcons.Actions.CloseNew.getIconWidth() + myIconTextGap), preferredSize.height);
        }

        @Override
        public Dimension getMinimumSize() {
          return getPreferredSize();
        }
      };

      actionLabel.append(String.valueOf(row), CAPTION_UNDERLINE_ATTRIBUTES);
      actionLabel.append(". ", new SimpleTextAttributes(0, CAPTION_COLOR));

      actionLabel.append(((ReopenProjectAction)action).getProjectName(), CAPTION_BOLD_UNDERLINE_ATTRIBUTES);
      actionLabel.setIconOnTheRight(true);

      String path = ((ReopenProjectAction)action).getProjectPath();
      File pathFile = new File(path);
      if (pathFile.isDirectory() && pathFile.getName().equals(((ReopenProjectAction)action).getProjectName())) {
        path = pathFile.getParent();
      }
      path = FileUtil.getLocationRelativeToUserHome(path);
      actionLabel.append("   " + path, new SimpleTextAttributes(SimpleTextAttributes.STYLE_SMALLER, Color.GRAY));

      actionLabel.setFont(new Font(CAPTION_FONT_NAME, Font.PLAIN, 12));
      actionLabel.setForeground(CAPTION_COLOR);
      actionLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

      new ClickListener() {
        @Override
        public boolean onClick(MouseEvent e, int clickCount) {
          if (e.getButton() == MouseEvent.BUTTON1) {
            DataContext dataContext = DataManager.getInstance().getDataContext(myWelcomePanel);
            int fragment = actionLabel.findFragmentAt(e.getX());
            if (fragment == SimpleColoredComponent.FRAGMENT_ICON) {
              final int rc = Messages.showOkCancelDialog(PlatformDataKeys.PROJECT.getData(dataContext),
                                                         "Remove '" + action.getTemplatePresentation().getText() +
                                                         "' from recent projects list?",
                                                         "Remove Recent Project",
                                                         Messages.getQuestionIcon());
              if (rc == 0) {
                final RecentProjectsManagerBase manager = RecentProjectsManagerBase.getInstance();
                assert action instanceof ReopenProjectAction : action;
                manager.removePath(((ReopenProjectAction)action).getProjectPath());
                final AnAction[] actions = manager.getRecentProjectsActions(false);
                if (actions.length == 0) {
                  hideRecentProjectsPanel();
                }
                else {
                  for (int i = myRecentProjectsPanel.getComponentCount() - 1; i >= 0; i--) {
                    myRecentProjectsPanel.remove(i);
                  }
                  setUpRecentProjectsPanel(rootPane, actions);
                  myRecentProjectsPanel.revalidate();
                }
              }
            }
            else if (fragment != -1) {
              AnActionEvent event = new AnActionEvent(e, dataContext, "", action.getTemplatePresentation(), ActionManager.getInstance(), 0);
              action.actionPerformed(event);
            }
          }
          return true;
        }
      }.installOn(actionLabel);

      actionLabel.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseEntered(MouseEvent e) {
          actionLabel.setIcon(AllIcons.Actions.CloseNew);
        }

        @Override
        public void mouseExited(MouseEvent e) {
          actionLabel.setIcon(EmptyIcon.create(AllIcons.Actions.CloseNew));
        }
      });
      actionLabel.setIcon(EmptyIcon.create(AllIcons.Actions.CloseNew));
      actionLabel.setOpaque(false);
      actionLabel.setIconOpaque(false);
      action.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_0 + row, InputEvent.ALT_DOWN_MASK)),
                                       rootPane, this);
      myRecentProjectsPanel.add(actionLabel, new GridBagConstraints(1, row++, 1, 1, 1, 0, NORTHWEST, HORIZONTAL, new Insets(5, 0, 5, 0), 0, 0));
      if (row == 9) break;
    }
  }

  public void hideRecentProjectsPanel() {
    myRecentProjectsPanel.setVisible(false);
  }

  private void setUpPluginsPanel() {
    GridBagConstraints gBC;

    JLabel pluginsCaption = new JLabel(UIBundle.message("welcome.screen.plugins.panel.plugins.label"));
    pluginsCaption.setFont(GROUP_CAPTION_FONT);
    pluginsCaption.setForeground(CAPTION_COLOR);

    JLabel openPluginManager = new JLabel(UIBundle.message("welcome.screen.plugins.panel.manager.link"));
    new ClickListener() {
      @Override
      public boolean onClick(MouseEvent e, int clickCount) {
        final PluginManagerConfigurable configurable = new PluginManagerConfigurable(PluginManagerUISettings.getInstance());
        ShowSettingsUtil.getInstance().editConfigurable(myPluginsPanel, configurable);
        return true;
      }
    }.installOn(openPluginManager);

    openPluginManager.setForeground(CAPTION_COLOR);
    openPluginManager.setFont(LINK_FONT);
    openPluginManager.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    JLabel installedPluginsCaption = new JLabel(UIBundle.message("welcome.screen.plugins.panel.my.plugins.label"));
    installedPluginsCaption.setFont(LINK_FONT);
    installedPluginsCaption.setForeground(CAPTION_COLOR);

    JPanel installedPluginsPanel = new JPanel(new GridBagLayout());
    installedPluginsPanel.setBackground(PLUGINS_PANEL_BACKGROUND);

    JLabel bundledPluginsCaption = new JLabel(UIBundle.message("welcome.screen.plugins.panel.bundled.plugins.label"));
    bundledPluginsCaption.setFont(LINK_FONT);
    bundledPluginsCaption.setForeground(CAPTION_COLOR);

    JPanel bundledPluginsPanel = new JPanel(new GridBagLayout());
    bundledPluginsPanel.setBackground(PLUGINS_PANEL_BACKGROUND);

    createListOfPlugins(installedPluginsPanel, bundledPluginsPanel);

    JPanel topPluginsPanel = new JPanel(new GridBagLayout());
    topPluginsPanel.setBackground(PLUGINS_PANEL_BACKGROUND);

    gBC = new GridBagConstraints(0, 0, 1, 1, 0, 0, NORTHWEST, NONE, PLUGINS_CAPTION_INSETS, 0, 0);
    topPluginsPanel.add(pluginsCaption, gBC);

    JLabel emptyLabel_1 = new JLabel();
    emptyLabel_1.setBackground(PLUGINS_PANEL_BACKGROUND);
    gBC = new GridBagConstraints(1, 0, 1, 1, 1, 0, NORTHWEST, NONE, NO_INSETS, 0, 0);
    topPluginsPanel.add(emptyLabel_1, gBC);

    gBC = new GridBagConstraints(2, 0, 1, 1, 0, 0, NORTHWEST, NONE, new Insets(22, 0, 0, 10), 0, 0);
    topPluginsPanel.add(openPluginManager, gBC);

    gBC = new GridBagConstraints(0, 0, 1, 1, 0, 0, NORTHWEST, HORIZONTAL, NO_INSETS, 0, 0);
    myPluginsPanel.add(topPluginsPanel, gBC);

    gBC = new GridBagConstraints(0, 1, 1, 1, 0, 0, NORTHWEST, NONE, new Insets(20, 25, 0, 0), 0, 0);
    myPluginsPanel.add(installedPluginsCaption, gBC);
    gBC = new GridBagConstraints(0, 2, 1, 1, 1, 0, NORTHWEST, NONE, new Insets(0, 5, 0, 0), 0, 0);
    myPluginsPanel.add(installedPluginsPanel, gBC);

    gBC = new GridBagConstraints(0, 3, 1, 1, 0, 0, NORTHWEST, NONE, new Insets(20, 25, 0, 0), 0, 0);
    myPluginsPanel.add(bundledPluginsCaption, gBC);
    gBC = new GridBagConstraints(0, 4, 1, 1, 1, 0, NORTHWEST, NONE, new Insets(0, 5, 0, 0), 0, 0);
    myPluginsPanel.add(bundledPluginsPanel, gBC);

    JPanel emptyPanel_1 = new JPanel();
    emptyPanel_1.setBackground(PLUGINS_PANEL_BACKGROUND);
    gBC = new GridBagConstraints(0, 5, 1, 1, 1, 1, NORTHWEST, BOTH, NO_INSETS, 0, 0);
    myPluginsPanel.add(emptyPanel_1, gBC);
  }

  private void createListOfPlugins(final JPanel installedPluginsPanel, final JPanel bundledPluginsPanel) {
    //Create the list of installed plugins
    List<IdeaPluginDescriptor> installedPlugins =
      new ArrayList<IdeaPluginDescriptor>(Arrays.asList(PluginManager.getPlugins()));

    if (installedPlugins.size() == 0) {
      addListItemToPlugins(installedPluginsPanel,
                           italic(UIBundle.message("welcome.screen.plugins.panel.no.plugins.currently.installed.message.text")));
      addListItemToPlugins(bundledPluginsPanel,
                           italic(UIBundle.message("welcome.screen.plugins.panel.all.bundled.plugins.were.uninstalled.message.text")));
    }
    else {
      final Comparator<IdeaPluginDescriptor> pluginsComparator = new Comparator<IdeaPluginDescriptor>() {
        public int compare(final IdeaPluginDescriptor o1, final IdeaPluginDescriptor o2) {
          final boolean e1 = o1.isEnabled();
          final boolean e2 = o2.isEnabled();
          if (e1 && !e2) return -1;
          if (!e1 && e2) return 1;
          return o1.getName().toLowerCase().compareTo(o2.getName().toLowerCase());
        }
      };
      Collections.sort(installedPlugins, pluginsComparator);

      int embeddedPlugins = 0;
      int installedPluginsCount = 0;

      for (IdeaPluginDescriptor plugin : installedPlugins) {
        if (plugin.getName().equals("IDEA CORE") || ((IdeaPluginDescriptorImpl)plugin).isUseCoreClassLoader()) {
          // this is not really a plugin, so it shouldn't be displayed
          continue;
        }
        if (plugin.isBundled()) {
          embeddedPlugins++;
          addListItemToPlugins(bundledPluginsPanel, (IdeaPluginDescriptorImpl)plugin);
        }
        else {
          installedPluginsCount++;
          addListItemToPlugins(installedPluginsPanel, (IdeaPluginDescriptorImpl)plugin);
        }
      }
      if (embeddedPlugins == 0) {
        addListItemToPlugins(bundledPluginsPanel,
                             italic(UIBundle.message("welcome.screen.plugins.panel.all.bundled.plugins.were.uninstalled.message.text")));
      }
      if (installedPluginsCount == 0) {
        addListItemToPlugins(installedPluginsPanel,
                             italic(UIBundle.message("welcome.screen.plugins.panel.no.plugins.currently.installed.message.text")));
      }
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static String italic(final String message) {
    return "<i>" + message + "</i>";
  }

  private void addListItemToPlugins(final JPanel bundledPluginsPanel, final String title) {
    addListItemToPlugins(bundledPluginsPanel, title, null, null, true, false);
  }

  private void addListItemToPlugins(final JPanel bundledPluginsPanel, final IdeaPluginDescriptorImpl plugin) {
    addListItemToPlugins(bundledPluginsPanel, plugin.getName(), plugin.getDescription(),
                         plugin.getUrl(), plugin.isEnabled(), PluginManager.isIncompatible(plugin));
  }

  public void addListItemToPlugins(final JPanel panel,
                                   String name,
                                   @Nullable String description,
                                   @Nullable final String url,
                                   final boolean enabled,
                                   final boolean incompatible) {
    if (StringUtil.isEmptyOrSpaces(name)) {
      return;
    }
    else {
      name = name.trim();
    }

    final int y = myPluginsIdx += 2;

    JLabel imageLabel = new JLabel(); // There used to be a logo, which is removed and I'm (max) lazy enough fixing gridbag
    GridBagConstraints gBC = new GridBagConstraints(0, y, 1, 1, 0, 0, NORTHWEST, NONE, new Insets(15, 20, 0, 0), 0, 0);
    panel.add(imageLabel, gBC);

    name = name + " " + (incompatible ? UIBundle.message("welcome.screen.incompatible.plugins.description")
                                      : (enabled ? "" : UIBundle.message("welcome.screen.disabled.plugins.description")));
    String shortenedName = adjustStringBreaksByWidth(name, LINK_FONT, false, PLUGIN_NAME_MAX_WIDTH, PLUGIN_NAME_MAX_ROWS);
    JLabel logoName = new JLabel(shortenedName);
    logoName.setFont(LINK_FONT);
    logoName.setForeground(enabled ? CAPTION_COLOR : DISABLED_CAPTION_COLOR);
    if (shortenedName.endsWith(___HTML_SUFFIX)) {
      logoName.setToolTipText(adjustStringBreaksByWidth(name, UIUtil.getToolTipFont(), false, MAX_TOOLTIP_WIDTH, 0));
    }

    JPanel logoPanel = new JPanel(new BorderLayout());
    logoPanel.setBackground(PLUGINS_PANEL_BACKGROUND);
    logoPanel.add(logoName, BorderLayout.WEST);
    gBC = new GridBagConstraints(1, y, 1, 1, 0, 0, NORTHWEST, NONE, new Insets(15, 7, 0, 0), 0, 0);
    panel.add(logoPanel, gBC);

    if (!StringUtil.isEmptyOrSpaces(url)) {
      JLabel learnMore = new JLabel(UIBundle.message("welcome.screen.plugins.panel.learn.more.link"));
      learnMore.setFont(LINK_FONT);
      learnMore.setForeground(enabled ? CAPTION_COLOR : DISABLED_CAPTION_COLOR);
      learnMore.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      learnMore.setToolTipText(UIBundle.message("welcome.screen.plugins.panel.learn.more.tooltip.text"));
      new ClickListener() {
        @Override
        public boolean onClick(MouseEvent e, int clickCount) {
          try {
            BrowserUtil.launchBrowser(url);
          }
          catch (IllegalThreadStateException ignore) {
          }
          return true;
        }
      }.installOn(learnMore);

      logoPanel.add(new JLabel(" "), BorderLayout.CENTER);
      logoPanel.add(learnMore, BorderLayout.EAST);
    }

    if (!StringUtil.isEmpty(description)) {
      //noinspection ConstantConditions
      description = XmlStringUtil.stripHtml(description.trim());
      description = description.replaceAll(ESC_NEW_LINE, "");
      String shortenedDcs = adjustStringBreaksByWidth(description, TEXT_FONT, false, PLUGIN_DSC_MAX_WIDTH, PLUGIN_DSC_MAX_ROWS);
      JLabel pluginDescription = new JLabel(shortenedDcs);
      pluginDescription.setFont(TEXT_FONT);
      if (shortenedDcs.endsWith(___HTML_SUFFIX)) {
        pluginDescription.setToolTipText(adjustStringBreaksByWidth(description, UIUtil.getToolTipFont(), false, MAX_TOOLTIP_WIDTH, 0));
      }

      gBC = new GridBagConstraints(1, y + 1, 1, 1, 0, 0, NORTHWEST, HORIZONTAL, new Insets(5, 7, 0, 0), 5, 0);
      panel.add(pluginDescription, gBC);
    }
  }

  /**
   * This method checks the width of the given string with given font applied, breaks the string into the specified number of lines if necessary,
   * and/or cuts it, so that the string does not exceed the given width (with ellipsis concatenated at the end if needed).<br>
   * It also removes all of the formatting HTML tags, except <b>&lt;br&gt;</b> and <b>&lt;li&gt;</b> (they are used for correct line breaks).
   * Returns the resulting or original string surrounded by <b>&lt;html&gt;</b> tags.
   *
   * @param string        not <code>null</code> {@link String String} value, otherwise the "Not specified." string is returned.
   * @param font          not <code>null</code> {@link Font Font} object.
   * @param isAntiAliased <code>boolean</code> value to denote whether the font is anti-aliased or not.
   * @param maxWidth      <code>int</code> value specifying maximum width of the resulting string in pixels.
   * @param maxRows       <code>int</code> value specifying the number of rows. If the value is positive, the string is modified to not exceed
   *                      the specified number, and method adds an ellipsis instead of the exceeding part. If the value is zero or negative,
   *                      the entire string is broken into lines until its end.
   * @return the resulting or original string ({@link String String}) surrounded by <b>&lt;html&gt;</b> tags.
   */
  @SuppressWarnings({"HardCodedStringLiteral"})
  private static String adjustStringBreaksByWidth(String string,
                                                  final Font font,
                                                  final boolean isAntiAliased,
                                                  final int maxWidth,
                                                  final int maxRows) {
    string = string.trim();
    if (StringUtil.isEmpty(string)) {
      return XmlStringUtil.wrapInHtml(UIBundle.message("welcome.screen.text.not.specified.message"));
    }

    string = string.replaceAll("<li>", " <>&gt; ");
    string = string.replaceAll("<br>", " <>");
    string = string.replaceAll("(<[^>]+?>)", " ");
    string = string.replaceAll("[\\s]{2,}", " ");
    Rectangle2D r = font.getStringBounds(string, new FontRenderContext(new AffineTransform(), isAntiAliased, false));

    if (r.getWidth() > maxWidth) {

      StringBuilder prefix = new StringBuilder();
      String suffix = string;
      int maxIdxPerLine = (int)(maxWidth / r.getWidth() * string.length());
      int lengthLeft = string.length();
      int rows = maxRows;
      if (rows <= 0) {
        rows = string.length() / maxIdxPerLine + 1;
      }

      while (lengthLeft > maxIdxPerLine && rows > 1) {
        int i;
        for (i = maxIdxPerLine; i > 0; i--) {
          if (suffix.charAt(i) == ' ') {
            prefix.append(suffix.substring(0, i)).append("<br>");
            suffix = suffix.substring(i + 1, suffix.length());
            lengthLeft = suffix.length();
            if (maxRows > 0) {
              rows--;
            }
            else {
              rows = lengthLeft / maxIdxPerLine + 1;
            }
            break;
          }
        }
        if (i == 0) {
          if (rows > 1 && maxRows <= 0) {
            prefix.append(suffix.substring(0, maxIdxPerLine)).append("<br>");
            suffix = suffix.substring(maxIdxPerLine, suffix.length());
            lengthLeft = suffix.length();
            rows--;
          }
          else {
            break;
          }
        }
      }
      if (suffix.length() > maxIdxPerLine) {
        suffix = suffix.substring(0, maxIdxPerLine - 3);
        for (int i = suffix.length() - 1; i > 0; i--) {
          if (suffix.charAt(i) == ' ') {
            if ("...".equals(suffix.substring(i - 3, i))) {
              suffix = suffix.substring(0, i - 1);
              break;
            }
            else if (suffix.charAt(i - 1) == '>') {
              //noinspection AssignmentToForLoopParameter
              i--;
            }
            else if (suffix.charAt(i - 1) == '.') {
              suffix = suffix.substring(0, i) + "..";
              break;
            }
            else {
              suffix = suffix.substring(0, i) + "...";
              break;
            }
          }
        }
      }
      string = prefix + suffix;
    }
    string = string.replaceAll(" <>", "<br>");
    return XmlStringUtil.wrapInHtml(string);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static String underlineHtmlText(final String commandLink) {
    return "<html><nobr><u>" + commandLink + "</u></nobr></html>";
  }

  private class ActionGroupDescriptor {
    private int myIdx = -1;
    private int myCount = 0;
    private final JPanel myPanel;
    private final int myColumnIdx;

    public ActionGroupDescriptor(final String caption, final int columnIndex) {
      JPanel panel = new JPanel(new GridBagLayout()) {
        public Dimension getPreferredSize() {
          return getMinimumSize();
        }
      };
      panel.setBackground(MAIN_PANEL_BACKGROUND);

      JLabel actionGroupCaption = new JLabel(caption);
      actionGroupCaption.setFont(GROUP_CAPTION_FONT);
      actionGroupCaption.setForeground(CAPTION_COLOR);

      GridBagConstraints gBC = new GridBagConstraints(0, 0, 2, 1, 0, 0, NORTHWEST, NONE, ACTION_GROUP_CAPTION_INSETS, 0, 0);
      panel.add(actionGroupCaption, gBC);
      myPanel = panel;
      myColumnIdx = columnIndex;
    }

    public void addButton(final MyActionButton button, String commandLink, String description) {
      GridBagConstraints gBC;

      final int y = myIdx += 2;
      gBC = new GridBagConstraints(0, y, 1, 2, 0, 0, NORTHWEST, NONE, ACTION_ICON_INSETS, ACTION_BUTTON_PADDING, ACTION_BUTTON_PADDING);
      myPanel.add(button, gBC);
      button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      button.setupWithinPanel(myMainPanel, MAIN_GROUP, myCount, myColumnIdx);
      myCount++;

      JLabel name = new JLabel(underlineHtmlText(commandLink));
      new ClickListener() {
        @Override
        public boolean onClick(MouseEvent event, int clickCount) {
          button.onPress(event);
          return true;
        }
      }.installOn(name);

      name.setForeground(CAPTION_COLOR);
      name.setFont(LINK_FONT);
      name.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      gBC = new GridBagConstraints(1, y, 1, 1, 0, 0, SOUTHWEST, NONE, ACTION_NAME_INSETS, 5, 0);
      myPanel.add(name, gBC);

      description = XmlStringUtil.wrapInHtml(description);
      JLabel shortDescription = new JLabel(description);
      shortDescription.setFont(TEXT_FONT);
      gBC = new GridBagConstraints(1, y + 1, 1, 1, 0, 0, NORTHWEST, HORIZONTAL, ACTION_DESCRIPTION_INSETS, 5, 0);
      myPanel.add(shortDescription, gBC);
    }

    private void appendActionsFromGroup(final ActionGroup group) {
      final AnAction[] actions = group.getChildren(null);
      PresentationFactory factory = new PresentationFactory();
      for (final AnAction action : actions) {
        if (action instanceof ActionGroup) {
          final ActionGroup childGroup = (ActionGroup)action;
          appendActionsFromGroup(childGroup);
        }
        else {
          Presentation presentation = factory.getPresentation(action);
          action.update(new AnActionEvent(null, DataManager.getInstance().getDataContext(myMainPanel),
                                          ActionPlaces.WELCOME_SCREEN, presentation, ActionManager.getInstance(), 0));
          if (presentation.isVisible()) {
            appendButtonForAction(action);
          }
        }
      }
    }

    public void appendButtonForAction(final AnAction action) {
      final Presentation presentation = action.getTemplatePresentation();
      final Icon icon = presentation.getIcon();
      final String text = presentation.getText();
      MyActionButton button = new ButtonWithExtension(icon, "") {
        protected void onPress(InputEvent e, MyActionButton button) {
          final ActionManager actionManager = ActionManager.getInstance();
          AnActionEvent evt = new AnActionEvent(
            null,
            DataManager.getInstance().getDataContext(e.getComponent()),
            ActionPlaces.WELCOME_SCREEN,
            action.getTemplatePresentation(),
            actionManager,
            0
          );
          action.beforeActionPerformedUpdate(evt);
          if (evt.getPresentation().isEnabled()) {
            action.actionPerformed(evt);
          }
        }
      };

      String description = presentation.getDescription();
      description = MessageFormat.format(description, ApplicationNamesInfo.getInstance().getFullProductName());
      addButton(button, text, description);
    }

    public JPanel getPanel() {
      return myPanel;
    }

    public int getIdx() {
      return myIdx;
    }
  }

  private abstract class MyActionButton extends JComponent implements ActionButtonComponent {
    private int myGroupIdx;
    private int myRowIdx;
    private int myColumnIdx;
    private final String myDisplayName;
    private final LabeledIcon myIcon;

    private MyActionButton(Icon icon, String displayName) {
      myDisplayName = displayName;
      myIcon = new LabeledIcon(icon != null ? icon : EmptyIcon.create(48), getDisplayName(), null);
    }

    private void setupWithinPanel(JPanel panel, int groupIdx, int rowIdx, int columnIdx) {
      myGroupIdx = groupIdx;
      myRowIdx = rowIdx;
      myColumnIdx = columnIdx;
      setToolTipText(null);
      setupListeners(panel);
    }

    protected String getDisplayName() {
      return myDisplayName != null ? myDisplayName : "";
    }

    public Dimension getMaximumSize() {
      return ACTION_BUTTON_SIZE;
    }

    public Dimension getMinimumSize() {
      return ACTION_BUTTON_SIZE;
    }

    public Dimension getPreferredSize() {
      return ACTION_BUTTON_SIZE;
    }

    protected void paintComponent(Graphics g) {
      UIUtil.applyRenderingHints(g);
      super.paintComponent(g);
      ActionButtonLook look = ActionButtonLook.IDEA_LOOK;
      paintBackground(g);
      look.paintIconAt(g, this, myIcon, 0, 5);
      paintBorder(g);
    }

    protected Color getNormalButtonColor() {
      return ACTION_BUTTON_COLOR;
    }

    protected void paintBackground(Graphics g) {
      Dimension dimension = getSize();
      int state = getPopState();
      if (state != NORMAL) {
        if (state == POPPED) {
          g.setColor(BUTTON_POPPED_COLOR);
          g.fillRect(0, 0, dimension.width, dimension.height);
        }
        else {
          g.setColor(BUTTON_PUSHED_COLOR);
          g.fillRect(0, 0, dimension.width, dimension.height);
        }
      }
      else {
        g.setColor(getNormalButtonColor());
        g.fillRect(0, 0, dimension.width, dimension.height);
      }
      if (state == PUSHED) {
        g.setColor(BUTTON_PUSHED_COLOR);
        g.fillRect(0, 0, dimension.width, dimension.height);
      }
    }

    public int getPopState() {
      if (myPressedButton == this) return PUSHED;
      if (myPressedButton != null) return NORMAL;
      if (mySelectedColumn == myColumnIdx &&
          mySelectedRow == myRowIdx &&
          mySelectedGroup == myGroupIdx) {
        return POPPED;
      }
      return NORMAL;
    }

    private void setupListeners(final JPanel panel) {
      addMouseListener(new MouseAdapter() {
        public void mousePressed(MouseEvent e) {
          myPressedButton = MyActionButton.this;
          panel.repaint();
        }

        public void mouseReleased(MouseEvent e) {
          if (myPressedButton == MyActionButton.this) {
            myPressedButton = null;
            onPress(e);
          }
          else {
            myPressedButton = null;
          }

          panel.repaint();
        }

        public void mouseExited(MouseEvent e) {
          mySelectedColumn = -1;
          mySelectedRow = -1;
          mySelectedGroup = -1;
          panel.repaint();
        }
      });

      addMouseMotionListener(new MouseMotionListener() {
        public void mouseDragged(MouseEvent e) {
        }

        public void mouseMoved(MouseEvent e) {
          mySelectedColumn = myColumnIdx;
          mySelectedRow = myRowIdx;
          mySelectedGroup = myGroupIdx;
          panel.repaint();
        }
      });
    }

    protected abstract void onPress(InputEvent e);
  }

  private abstract class ButtonWithExtension extends MyActionButton {
    private ButtonWithExtension(Icon icon, String displayName) {
      super(icon, displayName);
    }

    protected void onPress(InputEvent e) {
      onPress(e, this);
    }

    protected abstract void onPress(InputEvent e, MyActionButton button);
  }

  private static JScrollPane scrollPane(final JPanel panel, @Nullable final Color borderColor) {
    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(panel);
    scrollPane.setBorder(borderColor != null ?
                         BorderFactory.createLineBorder(borderColor, 1) : BorderFactory.createEmptyBorder());
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    return scrollPane;
  }

  private static class OpenProjectTransferHandler extends TransferHandler {
    private final JComponent myComponent;

    public OpenProjectTransferHandler(final JComponent component) {
      myComponent = component;
    }

    public boolean canImport(final TransferSupport support) {
      return FileCopyPasteUtil.isFileListFlavorSupported(support.getDataFlavors());
    }

    public boolean importData(final TransferSupport support) {
      final String projectPath = getProjectPath(support);
      if (projectPath == null) return false;
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          ReopenProjectAction action = new ReopenProjectAction(projectPath, "Any", "Any");
          DataContext dataContext = DataManager.getInstance().getDataContext(myComponent);
          AnActionEvent e = new AnActionEvent(null, dataContext, "", action.getTemplatePresentation(), ActionManager.getInstance(), 0);
          action.actionPerformed(e);
        }
      });
      return true;
    }

    @Nullable
    private static String getProjectPath(final TransferSupport support) {
      if (!FileCopyPasteUtil.isFileListFlavorSupported(support.getDataFlavors())) return null;
      List<File> files = FileCopyPasteUtil.getFileList(support.getTransferable());
      if (files == null) return null;
      File file = ContainerUtil.getFirstItem(files);
      return file != null && isProjectFileOrDir(file) ? file.getAbsolutePath() : null;
    }

    private static boolean isProjectFileOrDir(File file) {
      if (file.isFile() && file.getName().toLowerCase().endsWith(ProjectFileType.DOT_DEFAULT_EXTENSION)) return true;
      if (file.isDirectory() && new File(file, Project.DIRECTORY_STORE_FOLDER).isDirectory()) return true;
      return false;
    }
  }
}
