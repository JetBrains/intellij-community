// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.heatmap.actions

import com.intellij.internal.heatmap.fus.*
import com.intellij.internal.heatmap.settings.ClickMapSettingsDialog
import com.intellij.internal.statistic.beans.ConvertUsagesUtil
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.openapi.actionSystem.impl.ActionMenuItem
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.ui.JBColor
import com.intellij.ui.PopupMenuListenerAdapter
import com.intellij.util.PlatformUtils
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.IOException
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.JComponent
import javax.swing.MenuSelectionManager
import javax.swing.event.PopupMenuEvent


private val BUTTON_EMPTY_STATS_COLOR: Color = JBColor.GRAY.brighter()

val LOG = Logger.getInstance(ShowHeatMapAction::class.java)

class ShowHeatMapAction : AnAction(), DumbAware {

  companion object MetricsCache {
    private val toolbarsAllMetrics = mutableListOf<MetricEntity>()
    private val mainMenuAllMetrics = mutableListOf<MetricEntity>()
    private val toolbarsMetrics = hashMapOf<String, List<MetricEntity>>()
    private val mainMenuMetrics = hashMapOf<String, List<MetricEntity>>()
    private val toolbarsTotalMetricsUsers = hashMapOf<String, Int>()
    private val mainMenuTotalMetricsUsers = hashMapOf<String, Int>()

    //settings
    private var myEndStartDate: Pair<Date, Date>? = null
    private var myShareType: ShareType? = null
    private var myServiceUrl: String? = null
    private val myBuilds = mutableListOf<String>()
    private var myIncludeEap = false
    private var myColor = Color.RED

    private val ourIdeBuildInfos = mutableListOf<ProductBuildInfo>()

    fun getOurIdeBuildInfos(): List<ProductBuildInfo> = ourIdeBuildInfos
    fun getSelectedServiceUrl(): String = (myServiceUrl ?: DEFAULT_SERVICE_URLS.first()).trimEnd('/')
  }

  @Volatile
  private var myToolBarProgress = false
  @Volatile
  private var myMainMenuProgress = false
  private val ourBlackList = HashMap<String, String>()
  private val PRODUCT_CODE = getProductCode()

  init {
    ourBlackList["com.intellij.ide.ReopenProjectAction"] = "Reopen Project"
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = isInProgress().not()
    super.update(e)
  }

  private fun isInProgress(): Boolean = myToolBarProgress || myMainMenuProgress

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    if (ourIdeBuildInfos.isEmpty()) ourIdeBuildInfos.addAll(getProductBuildInfos(PRODUCT_CODE))
    askSettingsAndPaintUI(project)
  }

  private fun askSettingsAndPaintUI(project: Project) {
    val frame = WindowManagerEx.getInstance().getIdeFrame(project)
    if (frame == null) return

    val askSettings = ClickMapSettingsDialog(project)
    val ok = askSettings.showAndGet()
    if (ok.not()) return
    val serviceUrl = askSettings.getServiceUrl()
    if (serviceUrl == null) {
      showWarnNotification("Statistics fetch failed", "Statistic Service url is not specified", project)
      return
    }
    val startEndDate = askSettings.getStartEndDate()
    val shareType = askSettings.getShareType()
    val ideBuilds = askSettings.getBuilds()
    val isIncludeEap = askSettings.getIncludeEap()
    if (settingsChanged(startEndDate, shareType, serviceUrl, ideBuilds, isIncludeEap)) {
      clearCache()
    }
    myServiceUrl = serviceUrl
    myBuilds.clear()
    myBuilds.addAll(ideBuilds)
    myEndStartDate = startEndDate
    myIncludeEap = isIncludeEap
    myShareType = shareType
    myColor = askSettings.getColor()
    if (toolbarsAllMetrics.isNotEmpty()) {
      paintVisibleToolBars(project, frame.component, shareType, toolbarsAllMetrics)
    }
    else {
      val accessToken = askSettings.getAccessToken()
      if (StringUtil.isEmpty(accessToken)) {
        showWarnNotification("Statistics fetch failed", "access token is not specified", project)
        return
      }
      val startDate = getDateString(startEndDate.first)
      val endDate = getDateString(startEndDate.second)
      ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Fetching statistics for toolbar clicks", false) {
        override fun run(indicator: ProgressIndicator) {
          myToolBarProgress = true
          val toolBarAllStats = fetchStatistics(startDate, endDate, PRODUCT_CODE, myBuilds, arrayListOf(TOOLBAR_GROUP), accessToken!!)
          LOG.debug("\nGot Tool bar clicks stat: ${toolBarAllStats.size} rows:")
          toolBarAllStats.forEach {
            LOG.debug("Entity: $it")
          }
          if (toolBarAllStats.isNotEmpty()) toolbarsAllMetrics.addAll(toolBarAllStats)
          paintVisibleToolBars(project, frame.component, shareType, toolBarAllStats)
          myToolBarProgress = false
        }
      })
    }
    if (frame is IdeFrameImpl) {
      if (mainMenuAllMetrics.isNotEmpty()) {
        groupStatsByMenus(frame, mainMenuAllMetrics)
        addMenusPopupListener(frame, shareType)
      }
      else {
        val accessToken = askSettings.getAccessToken()
        if (StringUtil.isEmpty(accessToken)) {
          showWarnNotification("Statistics fetch failed", "access token is not specified", project)
          return
        }
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Fetching statistics for Main menu", false) {
          override fun run(indicator: ProgressIndicator) {
            myMainMenuProgress = true
            val startDate = getDateString(startEndDate.first)
            val endDate = getDateString(startEndDate.second)
            val menuAllStats = fetchStatistics(startDate, endDate, PRODUCT_CODE, myBuilds, arrayListOf(MAIN_MENU_GROUP), accessToken!!)
            if (menuAllStats.isNotEmpty()) {
              mainMenuAllMetrics.addAll(menuAllStats)
              groupStatsByMenus(frame, mainMenuAllMetrics)
              addMenusPopupListener(frame, shareType)
            }
            myMainMenuProgress = false
          }
        })
      }

    }
  }

  private fun groupStatsByMenus(frame: IdeFrameImpl, mainMenuAllStats: List<MetricEntity>) {
    val menus = frame.rootPane.jMenuBar.components
    for (menu in menus) {
      if (menu is ActionMenu) {
        val menuName = menu.text
        if (mainMenuMetrics[menuName] == null) {
          val menuStats = filterByMenuName(mainMenuAllStats, menuName)
          if (menuStats.isNotEmpty()) mainMenuMetrics[menuName] = menuStats
          var menuAllUsers = 0
          menuStats.forEach { menuAllUsers += it.users }
          if (menuAllUsers > 0) mainMenuTotalMetricsUsers[menuName] = menuAllUsers
        }
      }
    }
  }

  private fun paintActionMenu(menu: ActionMenu, allUsers: Int, level: Float) {
    val menuName = menu.text
    val menuAllUsers = mainMenuTotalMetricsUsers[menuName]
    menu.component.background = tuneColor(myColor, level)

    menu.addMouseListener(object : MouseAdapter() {
      override fun mouseEntered(e: MouseEvent?) {
        ActionMenu.showDescriptionInStatusBar(true, menu.component, "$menuName: $menuAllUsers usages of total $allUsers")
      }

      override fun mouseExited(e: MouseEvent?) {
        ActionMenu.showDescriptionInStatusBar(true, menu.component, "")
      }
    })
  }

  private fun addMenusPopupListener(frame: IdeFrameImpl, shareType: ShareType) {
    val menus = frame.rootPane.jMenuBar.components
    var mainMenuAllUsers = 0
    mainMenuTotalMetricsUsers.values.forEach { mainMenuAllUsers += it }

    for (menu in menus) {
      if (menu is ActionMenu) {
        paintMenuAndAddPopupListener(mutableListOf(menu.text), menu, mainMenuAllUsers, shareType)
      }
    }
  }

  private fun paintMenuAndAddPopupListener(menuGroupNames: MutableList<String>, menu: ActionMenu, menuAllUsers: Int, shareType: ShareType) {
    val level = if (menuGroupNames.size == 1) {
      val subMenuAllUsers = mainMenuTotalMetricsUsers[menuGroupNames[0]] ?: 0
      subMenuAllUsers / menuAllUsers.toFloat()
    }
    else {
      val subItemsStas: List<MetricEntity> = getMenuSubItemsStat(menuGroupNames)
      val maxUsers = subItemsStas.maxBy { m -> m.users }
      if (maxUsers != null) getLevel(shareType, maxUsers, MetricGroup.MAIN_MENU) else 0.001f
    }
    paintActionMenu(menu, menuAllUsers, level)
    val popupMenu = menu.popupMenu as? JBPopupMenu ?: return
    popupMenu.addPopupMenuListener(object : PopupMenuListenerAdapter() {

      override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) {
        val source = e.source as? JBPopupMenu ?: return
        val menuStats = mainMenuMetrics[menuGroupNames[0]] ?: return
        for (c in source.components) {
          if (c is ActionMenuItem) {
            paintMenuItem(c, menuGroupNames, menuStats, shareType)
          }
          else if (c is ActionMenu) {
            val newMenuGroup = menuGroupNames.toMutableList()
            newMenuGroup.add(newMenuGroup.lastIndex + 1, c.text)
            paintMenuAndAddPopupListener(newMenuGroup, c, menuAllUsers, shareType)
          }
        }
      }
    })
  }

  private fun getMenuSubItemsStat(menuGroupNames: MutableList<String>): List<MetricEntity> {
    val menuStat = mainMenuMetrics[menuGroupNames[0]] ?: emptyList()
    val pathPrefix = convertMenuItemsToKey(menuGroupNames)
    val result = mutableListOf<MetricEntity>()
    menuStat.forEach { if (it.id.startsWith(pathPrefix)) result.add(it) }
    return result
  }

  private fun paintMenuItem(menuItem: ActionMenuItem, menuGroupNames: List<String>, menuStats: List<MetricEntity>, shareType: ShareType) {
    val pathPrefix = convertMenuItemsToKey(menuGroupNames)
    val metricId = pathPrefix + MENU_ITEM_SEPARATOR + menuItem.text
    val menuItemMetric: MetricEntity? = findMetric(menuStats, metricId)

    if (menuItemMetric != null) {
      val color = calcColorForMetric(shareType, menuItemMetric, MetricGroup.MAIN_MENU)
      menuItem.isOpaque = true
      menuItem.text = menuItem.text + " (${menuItemMetric.users} / ${getMetricPlaceTotalUsersCache(menuItemMetric, MetricGroup.MAIN_MENU)})"
      menuItem.component.background = color
    }
    menuItem.addMouseListener(object : MouseAdapter() {

      override fun mouseEntered(e: MouseEvent) {
        //adds text to status bar and searching metrics if it was not found (using the path from MenuSelectionManager)
        val actionMenuItem = e.source
        if (actionMenuItem is ActionMenuItem) {
          val placeText = menuGroupNames.first()
          val action = actionMenuItem.anAction
          if (menuItemMetric != null) {
            val textWithStat = getTextWithStat(menuItemMetric, shareType,
                                               getMetricPlaceTotalUsersCache(menuItemMetric, MetricGroup.MAIN_MENU), placeText)
            val actionText = StringUtil.notNullize(menuItem.anAction.templatePresentation.text)
            ActionMenu.showDescriptionInStatusBar(true, menuItem, "$actionText: $textWithStat")
          }
          else {
            val pathStr = getPathFromMenuSelectionManager(action)
            if (pathStr != null) {
              val metric = findMetric(menuStats, pathStr)
              if (metric != null) {
                actionMenuItem.isOpaque = true
                actionMenuItem.component.background = calcColorForMetric(shareType, metric, MetricGroup.MAIN_MENU)

                val textWithStat = getTextWithStat(metric, shareType, getMetricPlaceTotalUsersCache(metric, MetricGroup.MAIN_MENU),
                                                   placeText)
                val actionText = StringUtil.notNullize(menuItem.anAction.templatePresentation.text)
                ActionMenu.showDescriptionInStatusBar(true, menuItem, "$actionText: $textWithStat")
              }
            }
          }
        }
      }

      override fun mouseExited(e: MouseEvent?) {
        ActionMenu.showDescriptionInStatusBar(true, menuItem, "")
      }
    })
  }

  private fun getPathFromMenuSelectionManager(action: AnAction): String? {
    val groups = MenuSelectionManager.defaultManager().selectedPath.filterIsInstance<ActionMenu>().map { o -> o.text }.toMutableList()
    if (groups.size > 0) {
      val text = getActionText(action)
      groups.add(text)
      return convertMenuItemsToKey(groups)
    }
    return null
  }

  private fun convertMenuItemsToKey(menuItems: List<String>): String {
    return menuItems.joinToString(MENU_ITEM_SEPARATOR)
  }

  private fun getActionText(action: AnAction): String {
    return ourBlackList[action.javaClass.name] ?: action.templatePresentation.text
  }


  private fun settingsChanged(startEndDate: Pair<Date, Date>,
                              shareType: ShareType,
                              currentServiceUrl: String,
                              ideBuilds: List<String>,
                              includeEap: Boolean): Boolean {
    if (currentServiceUrl != myServiceUrl) return true
    if (includeEap != myIncludeEap) return true
    if (ideBuilds.size != myBuilds.size) return true
    for (build in ideBuilds) {
      if (!myBuilds.contains(build)) return true
    }
    val prevStartEndDate = myEndStartDate
    if (prevStartEndDate != null) {
      if (!isSameDay(prevStartEndDate.first, startEndDate.first) || !isSameDay(prevStartEndDate.second, startEndDate.second)) {
        return true
      }
    }
    val prevShareType = myShareType
    if (prevShareType != null && prevShareType != shareType) {
      return true
    }
    return false
  }

  private fun isSameDay(date1: Date, date2: Date): Boolean {
    val c1 = Calendar.getInstance()
    val c2 = Calendar.getInstance()
    c1.time = date1
    c2.time = date2
    return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) && c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
  }

  private fun paintVisibleToolBars(project: Project, component: JComponent, shareType: ShareType, toolBarStats: List<MetricEntity>) {
    if (component is ActionToolbarImpl) paintToolBarButtons(component, shareType, toolBarStats)

    for (c in component.components) {
      when (c) {
        is ActionToolbarImpl -> paintToolBarButtons(c, shareType, toolBarStats)
        is JComponent -> paintVisibleToolBars(project, c, shareType, toolBarStats)
        else -> {
          LOG.debug("Unknown component: ${c.name}")
        }
      }
    }
  }

  private fun paintToolBarButtons(toolbar: ActionToolbarImpl, shareType: ShareType, toolBarAllStats: List<MetricEntity>) {
    val place = ConvertUsagesUtil.escapeDescriptorName(toolbar.place).replace(' ', '.')
    val toolBarStat = toolbarsMetrics[place] ?: filterByPlaceToolbar(toolBarAllStats, place)
    var toolbarAllUsers = 0
    toolBarStat.forEach { toolbarAllUsers += it.users }
    if (toolBarStat.isNotEmpty()) toolbarsMetrics[place] = toolBarStat
    if (toolbarAllUsers > 0) toolbarsTotalMetricsUsers[place] = toolbarAllUsers

    for (button in toolbar.components) {
      val action = (button as? ActionButton)?.action
                   ?: UIUtil.getClientProperty(button, CustomComponentAction.ACTION_KEY) ?: continue

      val id = getActionId(action)
      val buttonMetric = findMetric(toolBarStat, "$id@$place")

      val buttonColor = if (buttonMetric != null) calcColorForMetric(shareType, buttonMetric, MetricGroup.TOOLBAR)
      else BUTTON_EMPTY_STATS_COLOR
      val textWithStats = if (buttonMetric != null) getTextWithStat(buttonMetric, shareType, toolbarAllUsers, place) else ""
      val tooltipText = StringUtil.notNullize(action.templatePresentation.text) + textWithStats
      if (button is ActionButton) {
        (button as JComponent).toolTipText = tooltipText
        button.setLook(createButtonLook(buttonColor))
      }
      else if (button is JComponent) {
        button.toolTipText = tooltipText
        button.isOpaque = true
        button.background = buttonColor
      }
      LOG.debug("Place: $place action id: [$id]")
    }
  }

  private fun getTextWithStat(buttonMetric: MetricEntity, shareType: ShareType, placeAllUsers: Int, place: String): String {
    val totalUsers = if (shareType == ShareType.BY_GROUP) buttonMetric.sampleSize else placeAllUsers
    return "\nClicks: Unique:${buttonMetric.users} / Avg:${DecimalFormat("####.#").format(
      buttonMetric.usagesPerUser)} / ${place.capitalize()} total:$totalUsers"
  }

  private fun clearCache() {
    toolbarsAllMetrics.clear()
    toolbarsMetrics.clear()
    mainMenuAllMetrics.clear()
    mainMenuMetrics.clear()
    toolbarsTotalMetricsUsers.clear()
    mainMenuTotalMetricsUsers.clear()
  }

  private fun getActionId(action: AnAction): String {
    return ActionManager.getInstance().getId(action) ?: if (action is ActionWithDelegate<*>) {
      (action as ActionWithDelegate<*>).presentableName
    }
    else {
      action.javaClass.name
    }
  }

  private fun getDateString(date: Date): String = SimpleDateFormat(DATE_PATTERN).format(date)

  private fun findMetric(list: List<MetricEntity>, key: String): MetricEntity? {
    val metricId = ConvertUsagesUtil.escapeDescriptorName(key)
    list.forEach { if (it.id == metricId) return it }
    return null
  }

  private fun createButtonLook(color: Color): ActionButtonLook {
    return object : ActionButtonLook() {
      override fun paintBorder(g: Graphics, c: JComponent, state: Int) {
        g.drawLine(c.width - 1, 0, c.width - 1, c.height)
      }

      override fun paintBackground(g: Graphics, component: JComponent, state: Int) {
        if (state == ActionButtonComponent.PUSHED) {
          g.color = component.background.brighter()
          (g as Graphics2D).fill(g.getClip())
        }
        else {
          g.color = color
          (g as Graphics2D).fill(g.getClip())
        }
      }
    }
  }

  private fun getMetricPlaceTotalUsersCache(metricEntity: MetricEntity, group: MetricGroup): Int {
    return when (group) {
      MetricGroup.TOOLBAR -> toolbarsTotalMetricsUsers[getToolBarButtonPlace(metricEntity)] ?: -1
      MetricGroup.MAIN_MENU -> mainMenuTotalMetricsUsers[getMenuName(metricEntity)] ?: -1
    }
  }

  private fun calcColorForMetric(shareType: ShareType, metricEntity: MetricEntity, group: MetricGroup): Color {
    val level = getLevel(shareType, metricEntity, group)
    return tuneColor(myColor, level)
  }

  fun getLevel(shareType: ShareType, metricEntity: MetricEntity, group: MetricGroup): Float {
    return if (shareType == ShareType.BY_GROUP) {
      Math.max(metricEntity.share, 0.0001f) / 100.0f
    }
    else {
      val toolbarUsers = getMetricPlaceTotalUsersCache(metricEntity, group)
      if (toolbarUsers != -1) metricEntity.users / toolbarUsers.toFloat() else Math.max(metricEntity.share, 0.0001f) / 100.0f
    }
  }

  private fun tuneColor(default: Color, level: Float): Color {
    val r = default.red
    val g = default.green
    val b = default.blue
    val hsb = FloatArray(3)
    Color.RGBtoHSB(r, g, b, hsb)
    hsb[1] *= level
    val hsbBkg = FloatArray(3)
    val background = UIUtil.getLabelBackground()
    Color.RGBtoHSB(background.red, background.green, background.blue, hsbBkg)
    return JBColor.getHSBColor(hsb[0], hsb[1], hsbBkg[2])
  }

  private fun getProductCode(): String {
    var code = ApplicationInfo.getInstance().build.productCode
    if (StringUtil.isNotEmpty(code)) return code
    code = getProductCodeFromBuildFile()
    return if (StringUtil.isNotEmpty(code)) code else getProductCodeFromPlatformPrefix()
  }

  private fun getProductCodeFromBuildFile(): String {
    try {
      val home = PathManager.getHomePath()
      val buildTxtFile = FileUtil.findFirstThatExist(
        "$home/build.txt",
        "$home/Resources/build.txt",
        "$home/community/build.txt",
        "$home/ultimate/community/build.txt")
      if (buildTxtFile != null) {
        return FileUtil.loadFile(buildTxtFile).trim { it <= ' ' }.substringBefore('-', "")
      }
    }
    catch (ignored: IOException) {
    }
    return ""
  }

  private fun getProductCodeFromPlatformPrefix(): String {
    return when {
      PlatformUtils.isGoIde() -> "GO"
      PlatformUtils.isRider() -> "RD"
      else -> ""
    }
  }

}

enum class ShareType {
  BY_PLACE, BY_GROUP
}

private const val MENU_ITEM_SEPARATOR = " -> "
private const val MAIN_MENU_GROUP = "statistics.actions.main.menu"
private const val TOOLBAR_GROUP = "statistics.ui.toolbar.clicks"
private const val DATE_PATTERN = "YYYY-MM-dd"

enum class MetricGroup(val groupId: String) {
  TOOLBAR(TOOLBAR_GROUP), MAIN_MENU(MAIN_MENU_GROUP)
}

data class MetricEntity(val id: String,
                        val sampleSize: Int,
                        val groupSize: Int,
                        val users: Int,
                        val usages: Int,
                        val usagesPerUser: Float,
                        val share: Float)

data class ProductBuildInfo(val code: String, val type: String, val version: String, val majorVersion: String, val build: String) {
  fun isEap(): Boolean = type.equals("eap", true)
}