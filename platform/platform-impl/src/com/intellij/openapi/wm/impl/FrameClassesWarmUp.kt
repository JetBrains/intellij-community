// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.ide.ApplicationActivity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfoRt
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting

private val LOG = logger<FrameClassesWarmUp>()

/**
 * Loads in the background, at start-up, the classes that the EDT otherwise loads while it creates the first frame:
 * the project frame with its root pane, header, tool window pane and status bar, the modal welcome frame with its project tree,
 * and the recent project tree of the non-modal welcome screen.
 *
 * The activity loads the classes without initialization: it starts before the look and feel and the UI scale are set,
 * and some static initializers read them. [initializeFrameClasses] initializes them once the look and feel is installed.
 */
internal class FrameClassesWarmUp : ApplicationActivity {
  override suspend fun execute() {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      return
    }
    warmUpFrameClasses(initialize = false)
  }
}

/**
 * Initializes the frame classes, except [LOAD_ONLY_FRAME_CLASS_NAMES]. `ApplicationLoader` calls it once the look and feel is installed.
 * The other static initializers create companions, keys, loggers, icons and theme colors, or read the UI scale and the UI defaults.
 */
@Internal
fun initializeFrameClasses() {
  warmUpFrameClasses(initialize = true)
}

/** Loads the frame classes without initialization, for a frame that is shown before the look and feel is installed. */
@Internal
fun loadFrameClasses() {
  warmUpFrameClasses(initialize = false)
}

@VisibleForTesting
internal fun warmUpFrameClasses(initialize: Boolean) {
  val classLoader = FrameClassesWarmUp::class.java.classLoader
  for (className in frameClassNamesOfCurrentOs()) {
    if (initialize && className in LOAD_ONLY_FRAME_CLASS_NAMES) {
      continue
    }
    try {
      Class.forName(className, initialize, classLoader)
    }
    catch (e: ClassNotFoundException) {
      LOG.debug("Cannot warm up $className: $e")
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      LOG.warn("Cannot warm up $className", e)
    }
  }
}

@VisibleForTesting
internal fun frameClassNamesOfCurrentOs(): List<String> {
  return COMMON_FRAME_CLASS_NAMES + if (SystemInfoRt.isMac) MAC_FRAME_CLASS_NAMES else OTHER_OS_FRAME_CLASS_NAMES
}

/**
 * The classes of every OS, from the class loading logs of the frame creation, of the modal welcome frame, and of the recent project
 * tree of the non-modal welcome screen. The packages come in the order of their first use, and so do the names in a package.
 */
@VisibleForTesting
internal val COMMON_FRAME_CLASS_NAMES: List<String> = listOf(
  classNames(
    "com.intellij.openapi.wm.",
    "WelcomeFrameProvider", "WelcomeScreen", "WelcomeScreenTab", "WelcomeScreenLeftPanel", "WelcomeTabFactory",
    "WelcomeScreenCustomization", "StatusBarInfo", "StatusBar", "ToolWindowAnchor", "ToolWindowManager", "WindowInfo", "ToolWindowType",
    "ToolWindowContentUiType", "ToolWindow", "IdeGlassPane", "CustomStatusBarWidget", "IconLikeCustomStatusBarWidget",
    "IdeGlassPane\$TopComponent", "BaseFocusWatcher", "FocusWatcher", "IdeGlassPaneUtil", "StatusBarWidget", "StatusBarListener",
  ),
  classNames(
    "com.intellij.openapi.wm.impl.welcomeScreen.",
    "FlatWelcomeFrameProvider", "FlatWelcomeFrame", "FlatWelcomeFrame\$Companion", "AbstractWelcomeScreen", "TabbedWelcomeScreen",
    "ProjectsTabFactory", "CustomizeTabFactory", "PluginsTabFactory", "TreeWelcomeScreenLeftPanel", "WelcomeScreenUIManager",
    "MyCellRenderer", "TabbedWelcomeScreen\$DefaultWelcomeScreenTab", "WelcomeScreenComponentListener",
    "AbstractWelcomeScreen\$MyWelcomeScreenComponentListener", "WelcomeScreenComponentFactory", "RecentProjectPanel",
    "NewRecentProjectPanel", "WelcomeScreenComponentFactory\$JActionLinkPanel", "ProjectsTab", "WelcomeScreenEventCollector\$TabType",
    "ProjectsTabFactoryKt", "PanelState", "CustomizeTab", "CustomizeTab\$EntriesMappings", "PluginsTabFactory\$MyDefaultWelcomeScreenTab",
    "WelcomeScreenDefaultCustomization", "EmptyStateProjectsPanelKt", "WelcomeScreenSettingsKt", "NotificationEventAction",
    "WelcomeBalloonLayoutImpl", "WelcomeBalloonLayoutImpl\$BalloonPanel", "WelcomeBalloonLayoutImpl\$BalloonNotificationListener",
    "WelcomeScreenEventCollector", "WelcomeSeparateBalloonLayoutImpl", "WelcomeSeparateBalloonLayoutImpl\$ShowState",
    "WelcomeSeparateBalloonLayoutImpl\$ScrollController", "WelcomeSeparateBalloonLayoutImpl\$ScrollInfo", "FlatWelcomeFrameKt",
    "RecentProjectPanel\$FilePathChecker",
  ),
  classNames(
    "com.intellij.ui.components.",
    "JBSlidingPanel", "JBSlidingPanel\$AddMethodIsNotSupportedException", "JBLabel", "SplitButtonZones", "JBLayeredPane", "Interpolable",
    "JBScrollBar", "DefaultScrollBarUI", "ThinMacScrollBarUI", "ThinScrollBarUI", "DefaultScrollBarUI\$Companion",
    "DefaultScrollBarUI\$Listener", "DefaultScrollbarUiInstalledState", "ScrollBarPainter", "ScrollBarPainter\$Track", "TwoWayAnimator",
    "TwoWayAnimator\$MyAnimator", "ScrollBarPainter\$Thumb", "ScrollBarAnimationBehavior",
    "ToggleableScrollBarAnimationBehaviorDecorator", "DefaultScrollBarAnimationBehavior", "Interpolator", "Interpolator\$TimerListener",
    "JBScrollBar\$Model", "JBFontScaler", "JBTextField", "JBLoadingPanel", "JBList", "TextComponentEmptyText",
    "JBTextField\$AccessibleJBTextField",
  ),
  classNames(
    "com.intellij.ui.",
    "CardLayoutPanel", "ExpandableItemsHandler", "RetrievableIcon", "SmartExpander", "ProjectConceptBundle", "ColoredTextContainer",
    "SimpleColoredComponent", "ClickListener", "SimpleTextAttributes", "SimpleColoredComponent\$ImmutableFont",
    "SimpleColoredComponent\$ColoredIterator", "SimpleColoredComponent\$AccessibleSimpleColoredComponent",
    "SimpleColoredComponent\$ColoredFragment", "SimpleColoredComponent\$WidthKey", "SimpleColoredComponent\$FragmentTextClipper",
    "SimpleColoredComponent\$TextRenderer", "SimpleColoredComponent\$SimpleTextRenderer", "ExpandableItemsHandlerFactory",
    "ExpandableItemsHandlerFactoryImpl", "AbstractExpandableItemsHandler", "TreeExpandableItemsHandler",
    "AbstractExpandableItemsHandler\$ToolTipDetails", "ColoredTreeCellRenderer",
    "ColoredTreeCellRenderer\$AccessibleColoredTreeCellRendererWithContextMenu", "JBCardLayout\$SwipeDirection", "JBCardLayout",
    "CardLayoutPanel\$AccessibleCardLayoutPanel", "CardLayoutPanelScheduler", "BadgeIconSupplier", "AnchorableComponent",
    "DisabledTraversalPolicy", "SeparatorWithText", "LoadingNode", "ScreenUtil", "BalloonLayout", "DropAreaAware",
    "ToolbarDecoratorActionPromoter", "ComponentUtil", "EditorTextFieldActionPromoter", "PopupHandler", "UIBundle", "JBSplitter",
    "OnePixelSplitter", "MouseDragHelper", "AnimatedIcon", "AnimatedIcon\$Default", "SpinningProgressIcon", "AnimatedIcon\$Frame",
    "SpinningProgressIcon\$CashedDelegateIcon", "AnimatedIcon\$Big", "SpinningProgressIconKt", "BalloonImpl\$ShadowBorderProvider",
    "BalloonImpl\$ActionProvider", "ClientProperty", "AncestorListenerAdapter", "BalloonLayoutImpl", "ActionCenterBalloonLayout",
    "ComponentWithExpandableItems", "ComponentWithFileColors", "BorderPainter", "MixedColorProducer", "DefaultBorderPainter",
    "ClientProperty\$RecursiveListener", "AbstractBorderPainter", "ToolbarService", "ToolbarService\$Companion", "ToolbarServiceImpl",
    "ToolbarServiceImplKt", "JBAccountInfoService\$AccessTokenResult", "LicensingFacade\$ProductLicenseData", "GuiUtils",
    "TextFieldWithHistory", "ComponentTreeWatcher", "FilteringTree", "SearchTextField", "FilteringTree\$FilteringTreeUserObjectMatcher",
    "FilteringSpeedSearch", "SpeedSearchBase", "TreeSpeedSearch", "FilteringTree\$SearchTreeModel",
    "SearchTextField\$TextFieldWithProcessing", "SearchTextField\$MyModel", "DocumentAdapter",
  ),
  classNames(
    "com.intellij.util.ui.components.",
    "BorderLayoutPanel", "BorderLayoutPanel\$MyAccessibleContextDelegate",
  ),
  classNames(
    "com.intellij.ui.treeStructure.",
    "CachingTreePath", "TreeBulkExpansionEvent", "Tree\$NodeFilter", "TreeUiBulkExpandCollapseSupport", "TreeSwingModelListener",
    "Tree\$MyTreeModelListener", "Tree\$MySelectionModel", "Tree\$MyUISettingsListener", "Tree\$ExpandImpl", "Tree\$MyMouseListener",
    "Tree\$MyFocusListener", "TreeNodePresentation", "TreeNodePresentationImpl", "BgtAwareTreeModel",
    "Tree\$ExpandImpl\$TreeModelListenerImpl", "TreeNodeWithPresentation", "TreeNodeViewModel", "TreeSwingModel", "Tree",
  ),
  classNames(
    "com.intellij.util.ui.",
    "StatusText", "MouseEventAdapter", "NamedColorUtil", "JBUI\$CurrentTheme\$Tooltip", "JBUI\$CurrentTheme\$List\$Selection",
    "JBUI\$CurrentTheme\$Link\$Foreground", "StatusText\$Column", "StatusText\$Fragment", "GraphicsUtil", "AATextInfo",
    "MouseEventHandler", "JBRectangle", "UIUtil\$FontSize", "JBUI\$CurrentTheme\$ContextHelp", "JBUI\$Panels", "UIUtil\$ComponentStyle",
    "UIUtil\$FontColor", "JBUI\$CurrentTheme\$List", "JBUI\$CurrentTheme\$List\$Selection\$Inactive", "JBScalableIcon",
    "JBCachingScalableIcon", "EmptyIcon", "UIUtil", "HTMLEditorKitBuilder", "HTMLEditorKitBuilder\$Companion",
    "ExtendableHTMLViewFactory", "ExtendableHTMLViewFactory\$Companion", "ExtendableHTMLViewFactory\$Extension",
    "ExtendableHTMLViewFactory\$Extensions", "IconExtension", "WordWrapExtension", "InlineViewExExtension", "BlockViewExExtension",
    "FormViewExExtension", "ParagraphViewExExtension", "LineViewExExtension", "HiDpiImagesExtension", "FitToWidthImageViewExtension",
    "AdaptiveImageViewExtension", "FitToWidthAdaptiveImageViewExtension", "WbrSupportExtension", "BlockHrSupportExtension",
    "DetailsSummarySupportExtension", "DetailsSummarySupportExtension\$Companion", "StyleSheetUtil", "JBSwingUtilities",
    "JBHtmlEditorKit", "JBHtmlEditorKit\$StyleSheetCompressionThreshold", "JBHtmlEditorKit\$JBHtmlDocument",
    "JBHtmlEditorKit\$MouseExitSupportLinkController", "JBHtmlEditorKit\$LinkUnderlineListener",
    "JBHtmlEditorKit\$DetailsSummaryController", "JBHtmlEditorKit\$JBHtmlDocument\$JBHtmlReader",
    "JBHtmlEditorKit\$JBHtmlDocument\$CallbackWrapper", "TextLayoutUtil", "JBUI\$CurrentTheme\$Toolbar",
    "JBUI\$CurrentTheme\$CustomFrameDecorations", "JBUI\$CurrentTheme\$Separator", "JBUI\$CurrentTheme\$MainToolbar", "JBEmptyBorder",
    "AnimatedIcon", "AsyncProcessIcon", "Animator", "AnimatorKt", "AnimatedIcon\$HiddenAnimator", "JBImageIcon", "ImageUtil",
    "ShowingScopeKt", "UiScopeKt", "AbstractLayoutManager", "ComponentWithEmptyText", "RegionPainter", "JBInsets\$UIDefaultsSupplier",
    "JBFont\$JBFontUIResource", "JLabelUtil", "JBUI\$CurrentTheme\$StatusBar", "JBUI\$CurrentTheme\$StatusBar\$Widget",
    "JBUI\$CurrentTheme\$ActionButton", "SwingTextTrimmer", "SwingTextTrimmerStrategy", "StandardTrimmerStrategy", "SwingUndoUtil",
  ),
  classNames(
    "com.intellij.ui.tree.ui.",
    "CustomBoundsTreeUI", "DefaultTreeUI", "DefaultTreeLayoutCache", "Control", "DefaultControl", "DispatchThreadValidator", "TreeAction",
    "DefaultTreeUI\$MyTreeExpansionListener", "DefaultTreeLayoutCache\$NodeList", "DefaultTreeLayoutCache\$VariableHeightSupport",
    "DefaultTreeLayoutCache\$Location", "DefaultTreeLayoutCache\$Node", "DefaultTreeLayoutCacheKt", "Control\$Painter", "ClassicPainter",
  ),
  classNames(
    "com.intellij.ui.icons.",
    "ImageDataByPathLoaderKt", "ImageDataByPathLoader", "ImageDataByPathLoader\$Companion", "ReplaceableIcon", "IconReplacer",
    "ImageCacheKt", "ImageCache", "CacheKey", "HiDPIImage", "ScaledIconCacheKt", "EmptyImageDataLoader", "LoadIconParameters",
    "LoadIconParameters\$Companion", "ImageWithShape", "ScaledResultIcon", "PatchedRasterizedImageDataLoader", "PatchedIconDescriptor",
  ),
  classNames(
    "com.intellij.ide.dnd.",
    "SmoothAutoScroller", "SmoothAutoScroller\$DragListener", "DnDDropHandler", "DnDTargetChecker", "DnDTarget", "DnDNativeTarget",
    "DnDDropActionHandler", "DnDSource", "DnDDropHandler\$WithResult", "DnDSupport", "DnDSupportBuilder", "DnDEvent", "DnDManager",
    "DnDManagerImpl", "DnDManagerImpl\$NullTarget", "DnDManagerImpl\$MyDragGestureListener", "DnDManagerImpl\$MyDropTargetListener",
    "DnDSupport\$DnDNativeTargetWrapper",
  ),
  classNames(
    "com.intellij.ui.popup.list.",
    "SelectablePanel", "SelectablePanel\$Companion", "SelectablePanel\$SelectionArcCorners", "SelectablePanel\$Side",
  ),
  classNames(
    "com.intellij.ui.popup.",
    "MovablePopup", "AbstractPopup", "WizardPopup", "PopupState", "PopupState\$JBPopupState", "PopupState\$BalloonState",
    "PopupState\$JPopupMenuState",
  ),
  classNames(
    "com.intellij.util.ui.accessibility.",
    "AbstractAccessibleContextDelegate", "AccessibleContextDelegate", "AccessibleContextDelegateWithContextMenu",
    "AccessibleContextAccessor", "AccessibleContextUtil",
  ),
  classNames(
    "com.intellij.util.ui.update.",
    "ComparableObject", "Activatable", "UiNotifyConnector", "UiNotifyConnector\$Companion", "UiNotifyConnector\$ContextActivatable",
    "UiNotifyConnector\$ContextActivatable\$Companion", "DebouncedUpdates", "DebouncedUpdates\$Builder", "DebouncedUpdates\$ScopeBuilder",
    "DebouncedUpdates\$ScopeBuilderImpl", "DebouncedUpdates\$ScopeBuilderImpl\$Companion", "UpdateQueue", "BaseUpdateQueue",
    "BatchedScopeQueue",
  ),
  classNames(
    "com.intellij.util.ui.tree.",
    "TreeUtil", "TreeUtil\$MakeVisibleVisitor", "TreeUtil\$BulkMakeVisibleVisitor", "TreeUtil\$BackgroundMakeVisibleVisitor",
  ),
  classNames(
    "com.intellij.ui.tree.",
    "TreeVisitor", "EdtBgtTreeVisitor", "DelegatingEdtBgtTreeVisitor", "TreeVisitor\$Acceptor", "TreeVisitor\$LoadingAwareAcceptor",
    "TreeVisitor\$Action", "LeafState\$Supplier", "TreePathBackgroundSupplier",
  ),
  classNames(
    "com.intellij.ui.render.",
    "RenderingUtil",
  ),
  classNames(
    "com.intellij.ui.components.labels.",
    "LinkLabel", "ActionLink", "LinkLabel\$AccessibleLinkLabel", "LinkListener", "LinkLabel\$MyMouseHandler",
  ),
  classNames(
    "com.intellij.ide.ui.",
    "ToolbarSettings", "ExperimentalToolbarSettingsState", "ColorBlindnessSupport", "ColorBlindnessSupport\$Lazy", "LafReference",
    "IconDbMaintainer", "IconDbMaintainerKt", "ScreenAreaConsumer", "GradientTextureCache", "UISettingsUtilsKt", "ShowingContainer",
  ),
  classNames(
    "com.intellij.ide.ui.experimental.toolbar.",
    "ExperimentalToolbarSettings",
  ),
  classNames(
    "com.intellij.ui.layout.",
    "ComponentPredicate", "ValueComponentPredicate", "ComponentPredicate\$Companion",
  ),
  classNames(
    "com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.",
    "CloneableProjectsService", "CloneableProjectsService\$Companion", "CloneableProjectsService\$CloneProjectListener",
    "CloneableProjectsService\$CloneableProject",
  ),
  classNames(
    "com.intellij.openapi.observable.properties.",
    "PropertyGraph", "ObservableProperty", "ObservableMutableProperty", "GraphProperty", "AtomicMutableProperty",
    "AbstractObservableProperty", "AtomicLazyProperty", "GraphPropertyImpl", "AtomicLazyProperty\$Companion", "ListenerUtilKt",
    "ObservableBooleanProperty", "MutableBooleanProperty", "AtomicMutableBooleanProperty", "AbstractObservableBooleanProperty",
    "AtomicBooleanProperty",
  ),
  classNames(
    "com.intellij.openapi.observable.operation.core.",
    "ObservableOperationTrace", "ObservableTaskOperationTrace", "MutableOperationTrace", "AbstractObservableOperationTrace",
    "AtomicOperationTrace", "AtomicOperationTrace\$Companion", "ObservableOperationState", "AtomicOperationTrace\$State",
    "ObservableOperationStatus",
  ),
  classNames(
    "com.intellij.openapi.observable.dispatcher.",
    "SingleEventDispatcher", "SingleEventDispatcher\$Companion", "SingleEventDispatcher\$Multicaster", "AbstractSingleEventDispatcher",
    "AbstractSingleEventDispatcher\$RootDispatcher", "AbstractSingleEventDispatcher\$Companion", "SingleEventDispatcher2",
    "SingleEventDispatcher2\$Multicaster", "AbstractSingleEventDispatcher2", "AbstractSingleEventDispatcher2\$RootDispatcher",
    "SingleEventDispatcher0", "SingleEventDispatcher0\$Multicaster", "AbstractSingleEventDispatcher0",
    "AbstractSingleEventDispatcher0\$RootDispatcher", "AbstractSingleEventDispatcher\$ListenerWrapper",
    "AbstractSingleEventDispatcher\$ListenerWrapper\$Companion",
  ),
  classNames(
    "com.intellij.openapi.actionSystem.impl.",
    "ActionButton", "ActionButton\$AccessibleActionButton", "IdeaActionButtonLook", "ActionToolbarImpl", "ActionToolbarImpl\$Companion",
    "ActionButtonWithText", "ActionToolbarImpl\$MySeparator", "ActionToolbarImpl\$PopupToolbar",
    "ActionToolbarImpl\$SecondaryGroupUpdater", "ActionToolbarPresentationFactory", "ActionToolbarImpl\$ActionButtonBorder",
    "ActionToolbarImplKt",
  ),
  classNames(
    "com.intellij.openapi.wm.impl.",
    "FrameLoadingState", "ProjectFrameHelper", "IdeProjectFrameHelper", "ProjectFrameHelper\$Companion", "FrameBoundsConverter",
    "IdeFrameImpl\$Companion", "IdeFrameImpl\$FrameHelper", "WindowManagerImplKt", "WindowCloseListener", "IdeRootPane",
    "IdeRootPane\$CustomHeaderRootLayout", "IdeRootPaneKt", "AbstractDroppableStripe", "AbstractSquareStripeButton", "LayoutData",
    "AbstractDroppableStripe\$Companion", "StripeButtonSeparator", "ToolWindowManagerImpl", "IdeBackgroundUtil",
    "IdeBackgroundUtil\$MyTransform", "WindowInfoImpl", "WindowInfoImpl\$Companion", "ToolWindowAnchorEnum", "SquareStripeButtonKt",
    "SquareStripeButtonLook", "SquareStripeButtonLook\$Companion", "ToolWindowImpl", "IdeGlassPaneEx", "IdeGlassPaneImpl",
    "IdeGlassPaneImpl\$Companion", "PainterHelper", "IdePaneLoadingLayer", "PainterHelper\$ImagePainter", "PainterHelper\$MyImagePainter",
    "IdeFrameDecorator", "IdeFrameDecorator\$Companion", "IdeGlassPaneImplKt", "IdeRootPaneBorderHelper",
    "ProjectFrameCustomHeaderHelper", "ToolbarCreator", "ProjectFrameCustomHeaderHelperKt", "ToolbarHolder", "FrameHeaderHelper",
    "FrameHeaderHelper\$Decorated", "ProjectFrameHelperKt", "DialogBackgroundImageProviderBase",
    "DialogBackgroundImageProviderBase\$Companion", "IdeFrameDecoratorKt",
  ),
  classNames(
    "com.intellij.ide.ui.newItemPopup.",
    "NewItemSimplePopupPanel", "NewItemWithTemplatesPopupPanel",
  ),
  classNames(
    "com.intellij.openapi.ui.",
    "InputValidator", "InputValidatorEx", "Queryable\$Contributor", "Queryable", "UiUtils", "Splittable", "Divider", "OnePixelDivider",
    "OnePixelDivider\$MyMouseAdapter", "VerticalFlowLayout", "Painter", "ThreeComponentsSplitter", "Splitter",
    "ThreeComponentsSplitter\$Divider", "ThreeComponentsSplitter\$MyFocusTraversalPolicy", "NullableComponent\$Check",
    "ThreeComponentsSplitterKt", "Splitter\$DividerImpl", "Painter\$Listener", "ValidationInfo", "AbstractPainter",
    "DialogPanelValidator", "UiSwitcher", "UiSwitcher\$Companion", "DialogBackgroundImageProvider",
    "DialogBackgroundImageProvider\$Companion", "ThreeComponentsSplitter\$MyMouseAdapter", "MessageType", "FixedSizeButton",
    "ComboBoxWithWidePopup", "ComboBox", "ErrorBorderCapable",
  ),
  classNames(
    "com.intellij.openapi.wm.ex.",
    "IdeFrameEx", "ToolWindowManagerEx", "ToolWindowEx", "ProjectFrameCapabilitiesService", "ProjectFrameCapabilitiesService\$Companion",
    "ProjectFrameCapability", "ProjectFrameCapabilitiesProvider", "ProjectFrameCapabilitiesKt", "CachedCapabilities",
    "IdeFocusTraversalPolicy", "IdeFocusTraversalPolicy\$SwingDefaultFocusTraversalPolicy", "StatusBarEx",
  ),
  classNames(
    "com.github.weisj.jsvg.paint.impl.",
    "MaskedPaint",
  ),
  classNames(
    "com.intellij.ui.components.panels.",
    "OpaquePanel", "HorizontalLayout", "HorizontalLayout\$Companion", "HorizontalLayout\$Group", "HorizontalLayoutKt",
  ),
  classNames(
    "com.intellij.ui.scale.",
    "AbstractScaleContextAware", "ScaleContextCache",
  ),
  classNames(
    "com.intellij.ui.tabs.",
    "JBTabs", "JBTabsEx", "JBTabsPresentation", "TabsListener", "TabInfo\$DragOutDelegate", "TabInfo\$DragDelegate", "UiDecorator",
  ),
  classNames(
    "com.intellij.ui.switcher.",
    "QuickActionProvider",
  ),
  classNames(
    "com.intellij.ui.tabs.impl.",
    "MorePopupAware", "JBTabsImpl", "TabLayout", "TabLabel", "JBTabsImpl\$Companion", "JBTabsImpl\$DefaultDecorator",
  ),
  classNames(
    "com.intellij.ui.dsl.builder.",
    "KotlinUiDslService\$Companion", "Align\$Companion",
  ),
  classNames(
    "com.intellij.openapi.application.impl.islands.",
    "IslandsUICustomizationKt", "WindowBackgroundComponentData", "IslandsGradientPainter",
  ),
  classNames(
    "com.intellij.toolWindow.",
    "ToolWindowPane", "ToolWindowPane\$Companion", "ToolWindowButtonManager", "ToolWindowPaneNewButtonManager", "ToolWindowToolbar",
    "ToolWindowLeftToolbar", "ToolWindowToolbar\$Companion", "ToolWindowToolbar\$StripeV2", "AbstractMoreSquareStripeButton",
    "MoreSquareStripeButton", "StripeButtonManager", "ResizeStripeManager", "ResizeStripeManager\$Companion", "ToolWindowDropArea",
    "ShowMoreToolWindowsAction", "MoreSquareStripeButtonKt", "ToolWindowRightToolbar",
    "ToolWindowPane\$addAndSplitDockedComponentCmd\$MySplitter", "ToolWindowPaneState", "FrameLayeredPane", "ImageRef", "ImageCache",
    "ToolWindowDragHelper", "ToolWindowDragHelper\$Companion", "ToolWindowPaneKt",
  ),
  classNames(
    "com.intellij.ide.ui.laf.darcula.ui.",
    "DarculaEditorPaneUI", "DarculaLabelUI", "DarculaProgressBarUI", "DarculaProgressBarBorder", "TextFieldWithPopupHandlerUI",
    "DarculaTextFieldUI", "TextFieldWithPopupHandlerUI\$IconButton", "TextFieldWithPopupHandlerUI\$Handler", "DarculaTextBorder",
    "DarculaTextBorderNew", "TextFieldWithPopupHandlerUI\$MarginAwareCaret", "TextFieldWithPopupHandlerUI\$MouseDragAwareCaret",
    "TextFieldWithPopupHandlerUI\$SearchExtension", "TextFieldWithPopupHandlerUI\$IconHolder",
    "TextFieldWithPopupHandlerUI\$ClearExtension",
  ),
  classNames(
    "com.intellij.toolWindow.extendedToolWindowsUi.",
    "ToolWindowStripeExtension", "ToolWindowStripeExtension\$Companion",
  ),
  classNames(
    "com.intellij.ui.drag.",
    "DragButton",
  ),
  classNames(
    "com.intellij.ui.awt.",
    "DevicePoint", "RelativePoint",
  ),
  classNames(
    "com.intellij.util.ui.html.",
    "GlyphViewFix", "UtilsKt", "CssAttributesEx",
  ),
  classNames(
    "com.intellij.openapi.ui.popup.",
    "LightweightWindow", "JBPopup", "AlignedPopup", "BalloonHandler", "ListItemDescriptor",
  ),
  classNames(
    "com.intellij.openapi.actionSystem.ex.",
    "ActionButtonLook",
  ),
  classNames(
    "com.intellij.ui.speedSearch.",
    "ElementFilter", "SpeedSearchActivator", "SpeedSearch",
  ),
  classNames(
    "com.intellij.toolWindow.innerDrag.",
    "ToolWindowInnerDragHelper", "ToolWindowInnerDragHelper\$Companion",
  ),
  classNames(
    "com.intellij.ide.actions.",
    "DistractionFreeModeController",
  ),
  classNames(
    "com.intellij.util.animation.",
    "AlphaAnimated", "AlphaAnimationContext", "ShowHideAnimator", "Easing", "CubicBezierEasing", "JBAnimator", "JBAnimator\$FrameCounter",
    "JBAnimator\$Type", "Easing\$Stateful",
  ),
  classNames(
    "com.intellij.openapi.actionSystem.toolbarLayout.",
    "ToolbarLayoutStrategy", "RightActionsAdjusterStrategyWrapper", "NoWrapLayoutStrategy", "WrapLayoutStrategy", "AutoLayoutStrategy",
    "CompressingLayoutStrategy", "ToolbarLayoutUtilKt",
  ),
  classNames(
    "com.github.weisj.jsvg.parser.impl.",
    "AttributeParser\$RawTransformFunction",
  ),
  classNames(
    "com.github.weisj.jsvg.attributes.transform.",
    "TransformPart\$TransformType", "TransformPart",
  ),
  classNames(
    "com.github.weisj.jsvg.attributes.value.",
    "ConstantTransform",
  ),
  classNames(
    "com.intellij.ui.svg.",
    "AdaptiveImageLoadKt", "ImageDimension", "ImageDimension\$Unit",
  ),
  classNames(
    "com.github.weisj.jsvg.geometry.",
    "SVGRoundRectangle",
  ),
  classNames(
    "com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.",
    "RecentProjectFrameCapabilitiesProvider", "RecentProjectProgressTracker", "RecentProjectProgressTracker\$Companion", "ProjectsProgress",
    "ProjectsProgress\$Companion", "RecentProjectTreeItem", "CloneableProjectItem", "RecentProjectFilteringTree",
    "RecentProjectFilteringTree\$Companion", "RootItem", "RecentProjectFilteringTree\$ProjectActionButtonViewModel",
    "ProviderRecentProjectItem", "RecentProjectFilteringTree\$ProjectActionMouseListener",
    "RecentProjectFilteringTree\$ToggleStateListener", "RecentProjectFilteringTree\$ProjectActionRenderer",
    "RecentProjectFilteringTree\$ProjectActionRenderer\$RowComponent",
    "RecentProjectFilteringTree\$ProjectActionRenderer\$RecentProjectComponent", "RecentProjectItem", "RowButtonSlots", "ActionsButton",
    "ActionsButton\$Companion", "RecentProjectRowButton", "RecentProjectRowButton\$VcsActions",
    "RecentProjectFilteringTree\$ProjectActionRenderer\$ProjectGroupComponent", "ProjectsGroupItem",
    "RecentProjectFilteringTree\$ProjectActionRenderer\$CloneableProjectComponent",
    "RecentProjectFilteringTree\$FullRendererComponentTreeUI", "RecentProjectFilteringTree\$MouseHoverListener",
    "RecentProjectPanelComponentFactoryCoroutineScopeHolder",
  ),
  classNames(
    "com.intellij.openapi.ui.validation.",
    "DialogValidation", "DialogValidationRequestor",
  ),
  classNames(
    "com.intellij.ide.",
    "ProjectWindowCustomizerService", "ProjectWindowCustomizerService\$Companion", "ProjectWindowCustomizerServiceKt",
    "RecentProjectListActionProvider", "RecentProjectListActionProvider\$Companion", "RecentProjectListActionProviderKt",
    "ProjectNameOrPathIfNotYetComputed", "ProjectGroupComparator", "RecentProjectProvider", "RecentProject",
  ),
  classNames(
    "com.intellij.ui.docking.",
    "DockContainer", "DockContainer\$Persistent", "DockManager",
  ),
  classNames(
    "com.intellij.ui.tabs.impl.singleRow.",
    "SingleRowLayout",
  ),
  classNames(
    "com.intellij.ui.docking.impl.",
    "DockManagerImpl", "DockManagerImpl\$Companion",
  ),
  classNames(
    "com.intellij.openapi.wm.impl.customFrameDecorations.header.",
    "CustomWindowHeaderUtil", "MainFrameCustomHeader",
  ),
  classNames(
    "com.intellij.platform.ide.menu.",
    "IdeMenuFlavor", "IdeMenuBarHelper\$MenuBarImpl", "ActionAwareIdeMenuBar", "IdeMenuBarHelper", "IdeMenuBarHelperKt",
  ),
  classNames(
    "com.intellij.openapi.wm.impl.headertoolbar.",
    "MainToolbar", "MainToolbarFlavor", "ToolbarWidthCalculationEvent", "MainToolbarFocusSupport", "ToolbarWidthCalculationListener",
    "DefaultMainToolbarFlavor", "MainToolbarFocusSupport\$MainToolbarFocusTraversalPolicy", "MainToolbarFocusSupportKt", "MainToolbarKt",
    "GroupInfo",
  ),
  classNames(
    "com.intellij.ide.ui.customization.",
    "ActionUrl", "CustomizationUtil", "CustomizationUtil\$PopupComputableActionGroup", "CustomisedActionGroup",
    "CustomizationUtil\$MyDumbAction",
  ),
  classNames(
    "com.intellij.util.ui.table.",
    "ComponentsListFocusTraversalPolicy", "JBTableRowEditor",
  ),
  classNames(
    "com.intellij.ui.paint.",
    "PaintUtil\$RoundingMode", "PaintUtil",
  ),
  classNames(
    "com.intellij.openapi.wm.impl.status.",
    "IdeStatusBarImpl", "IdeStatusBarImpl\$Companion", "Position", "IdeStatusBarImpl\$WidgetEffect", "InfoAndProgressPanel",
    "WidgetRegistry", "ChildStatusBarManager", "WidgetEffectRenderer", "WidgetBean", "WidgetEffectRenderer\$Companion",
    "IdeStatusBarImpl\$WidgetSorter", "StatusBarPanel", "IdeStatusBarImpl\$WidgetDragHelper", "IdeStatusBarImpl\$WidgetDropPlaceholder",
    "StatusBarUI", "TextPanel", "TextPanel\$Companion", "IdeStatusBarImpl\$StatusBarFocusTraversalPolicy",
  ),
  classNames(
    "com.intellij.openapi.wm.impl.status.widget.",
    "StatusBarWidgetSettings", "StatusBarWidgetSettings\$Companion", "StatusBarState",
  ),
  classNames(
    "com.intellij.ui.border.",
    "NamedBorderKt", "NamedBorder", "NamedBorderImpl",
  ),
  classNames(
    "com.intellij.ui.util.",
    "UiSizeUtilKt",
  ),
  classNames(
    "com.intellij.openapi.",
    "MnemonicHelper", "MnemonicHelper\$MnemonicFixer",
  ),
  classNames(
    "com.intellij.ide.util.treeView.",
    "CachedTreePresentation", "NodeRenderer",
  ),
  classNames(
    "com.intellij.navigation.",
    "ColoredItemPresentation", "LocationPresentation",
  ),
  classNames(
    "com.intellij.ide.projectView.",
    "PresentationData",
  ),
  classNames(
    "com.intellij.ide.gdpr.",
    "DataSharingSettingsChangeListener", "DataSharingSettingsChangeListener\$Companion",
  ),
  classNames(
    "com.intellij.openapi.ui.panel.",
    "PanelBuilder", "GridBagPanelBuilder", "ComponentPanelBuilder", "ComponentPanelBuilder\$Companion",
    "ComponentPanelBuilder\$CommentLabel",
  ),
  classNames(
    "com.intellij.icons.",
    "AllIcons\$Vcs", "AllIcons\$Ide\$Notification",
  ),
  classNames(
    "com.intellij.internal.statistic.eventLog.validator.storage.",
    "ValidationTestRulesStorageHolder", "CompositeValidationRulesStorage", "IntellijValidationRulesStorage",
    "ValidationTestRulesPersistedStorage",
  ),
  classNames(
    "com.intellij.internal.statistic.eventLog.validator.storage.persistence.",
    "BaseEventLogMetadataPersistence", "EventLogMetadataPersistence", "EventLogTestMetadataPersistence",
  ),
  classNames(
    "com.intellij.internal.statistic.eventLog.connection.metadata.",
    "EventLogMetadataUpdateError", "EventLogMetadataParseException", "EventLogMetadataUtils", "EventLogMetadataLoadException",
  ),
  classNames(
    "com.intellij.internal.statistic.eventLog.connection.request.",
    "StatsResponseException",
  ),
  classNames(
    "com.intellij.internal.statistic.config.",
    "SerializationHelper",
  ),
  classNames(
    "com.intellij.util.",
    "IconSizeWrapper", "FontUtil\$DisableKerning",
  ),
  classNames(
    "com.intellij.ui.components.fields.",
    "ExtendableTextComponent\$Extension",
  ),
  classNames(
    "com.intellij.ide.ui.laf.",
    "MouseDragSelectionEventHandler",
  ),
  classNames(
    "com.intellij.ide.ui.laf.darcula.",
    "DarculaUIUtil",
  ),
  classNames(
    "com.intellij.util.text.",
    "Matcher",
  ),
).flatten()

/**
 * The macOS classes of the same logs: the frame decorator, the window tabs, the screen menu and the toolbar header views.
 */
@VisibleForTesting
internal val MAC_FRAME_CLASS_NAMES: List<String> = listOf(
  classNames(
    "com.intellij.ui.mac.",
    "MergeAllWindowsAction", "MacWinTabsHandler", "MacWinTabsHandlerV2", "WindowTabsComponent", "MacMainFrameDecorator",
    "MacMainFrameDecorator\$Companion", "MacMainFrameDecorator\$FSListener", "WindowTabsComponent\$WindowDropAreaPainter",
    "MacMainFrameDecorator\$FSAdapter", "MacFullScreenControlsManager",
  ),
  classNames(
    "com.intellij.ui.",
    "MacCustomAppIcon", "MacCustomAppIcon\$Companion",
  ),
  classNames(
    "com.intellij.ui.mac.touchbar.",
    "TouchbarSupport",
  ),
  classNames(
    "com.intellij.ui.tabs.impl.singleRow.",
    "WindowTabsLayout",
  ),
  classNames(
    "com.intellij.platform.ide.menu.",
    "MacMenuBarKt", "PeerBasedIdeMenuBarHelper",
  ),
  classNames(
    "com.intellij.ui.mac.screenmenu.",
    "MenuBar",
  ),
  classNames(
    "com.intellij.openapi.wm.impl.customFrameDecorations.header.",
    "MacToolbarFrameHeader", "HeaderView", "ToolbarHeaderView", "MacToolbarFrameHeaderKt", "CompactHeaderView",
  ),
  classNames(
    "com.intellij.ui.components.",
    "MacScrollBarUI", "MacScrollBarUI\$Companion", "MacScrollbarNative", "MacScrollBarUIKt", "MacScrollbarStyle",
    "MacScrollBarAnimationBehavior",
  ),
  classNames(
    "com.intellij.util.ui.",
    "MacScrollbarPreferences", "MacScrollbarPreferences\$Bindings", "MacScrollbarPreferences\$Observer",
  ),
  classNames(
    "com.intellij.ui.mac.foundation.",
    "MacUtil",
  ),
).flatten()

/**
 * The Linux and Windows classes of the frame decorator, the custom frame header and the menu, read from the code. They are only loaded.
 */
@VisibleForTesting
internal val OTHER_OS_FRAME_CLASS_NAMES: List<String> = listOf(
  classNames(
    "com.intellij.openapi.wm.impl.",
    "WinMainFrameDecorator", "EWMHFrameDecorator", "WLFrameDecorator",
  ),
  classNames(
    "com.intellij.openapi.wm.impl.customFrameDecorations.header.",
    "CustomHeader", "CustomFrameTopBorder", "FrameHeader", "DefaultFrameHeader", "MenuFrameHeader",
  ),
  classNames(
    "com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar.",
    "ToolbarFrameHeader", "ExpandableMenu", "MainMenuButton", "IdeMenuHelper", "ShowMode",
  ),
  classNames(
    "com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel.",
    "SimpleCustomDecorationPath", "CustomDecorationPath", "CustomDecorationTitle", "DefaultPartTitle", "ProjectTitlePane", "ProjectTitle",
    "SelectedEditorFilePath", "TitlePart", "BaseTitlePart", "ShrinkingTitlePart", "ClippingTitle", "ClassTitlePane",
  ),
  classNames(
    "com.intellij.openapi.wm.impl.customFrameDecorations.frameButtons.",
    "CustomFrameButtons", "LinuxCustomFrameButtons", "TitleButtonsPanel", "LinuxFrameButton", "IconSet", "LinuxIconThemeConfiguration",
    "LinuxResizableCustomFrameButtons",
  ),
  classNames(
    "com.intellij.openapi.wm.impl.headertoolbar.",
    "MainMenuWithButton", "MenuButtonInToolbarMainToolbarFlavor",
  ),
  classNames(
    "com.intellij.platform.ide.menu.",
    "IdeJMenuBar", "IdeMenuBarState", "JMenuBasedIdeMenuBarHelper", "WinAltKeyProcessor", "FloatingMenuBarFlavor",
  ),
).flatten()

/**
 * The classes that the warm-up only loads. Their static initializers start the native scroll bar observer, paint the background,
 * create images, set up the HTML kit, or read the toolkit, so they run on the EDT as before.
 * The initializers of [com.intellij.util.animation.Easing] and [com.intellij.util.animation.CubicBezierEasing] need each other,
 * and the initializer of `Easing.Stateful` starts the one of `Easing`. Two threads that enter this cycle at different classes
 * wait for each other forever.
 */
@VisibleForTesting
internal val LOAD_ONLY_FRAME_CLASS_NAMES: Set<String> = listOf(
  classNames(
    "com.intellij.util.ui.",
    "GraphicsUtil", "ExtendableHTMLViewFactory", "ExtendableHTMLViewFactory\$Companion", "ExtendableHTMLViewFactory\$Extension",
    "ExtendableHTMLViewFactory\$Extensions", "DetailsSummarySupportExtension", "DetailsSummarySupportExtension\$Companion",
    "JBHtmlEditorKit", "JBHtmlEditorKit\$StyleSheetCompressionThreshold", "JBHtmlEditorKit\$JBHtmlDocument",
    "JBHtmlEditorKit\$MouseExitSupportLinkController", "JBHtmlEditorKit\$LinkUnderlineListener",
    "JBHtmlEditorKit\$DetailsSummaryController", "JBHtmlEditorKit\$JBHtmlDocument\$JBHtmlReader",
    "JBHtmlEditorKit\$JBHtmlDocument\$CallbackWrapper", "MacScrollbarPreferences", "MacScrollbarPreferences\$Bindings",
    "MacScrollbarPreferences\$Observer",
  ),
  classNames(
    "com.intellij.openapi.wm.impl.",
    "IdeBackgroundUtil", "IdeBackgroundUtil\$MyTransform",
  ),
  classNames(
    "com.intellij.util.ui.html.",
    "GlyphViewFix",
  ),
  classNames(
    "com.intellij.ui.icons.",
    "ScaledIconCacheKt",
  ),
  classNames(
    "com.intellij.ide.dnd.",
    "DnDManagerImpl", "DnDManagerImpl\$NullTarget", "DnDManagerImpl\$MyDragGestureListener", "DnDManagerImpl\$MyDropTargetListener",
  ),
  classNames(
    "com.intellij.ui.components.",
    "ThinMacScrollBarUI", "MacScrollBarUI", "MacScrollBarUI\$Companion", "MacScrollbarNative", "MacScrollBarUIKt", "MacScrollbarStyle",
    "MacScrollBarAnimationBehavior",
  ),
  classNames(
    "com.intellij.ui.mac.foundation.",
    "MacUtil",
  ),
  classNames(
    "com.intellij.util.animation.",
    "Easing", "CubicBezierEasing", "Easing\$Stateful",
  ),
).flatten().toHashSet() + OTHER_OS_FRAME_CLASS_NAMES

private fun classNames(packagePrefix: String, vararg simpleNames: String): List<String> = simpleNames.map { packagePrefix + it }
