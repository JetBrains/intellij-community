// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util

/** The names the matcher benchmarks read. A benchmark that needs another shape of a name derives it from these lists. */
internal object MatcherBenchmarkCorpus {

  /**
   * Symbol names of a real solution, from 3 to 50 characters.
   *
   * The benchmarks match fixed patterns against this list. The list must hold a hit for each pattern. It needs a name
   * that starts with `Solution`, a name that holds `Declaration`, and `AbstractPropertyDeclaration` for the acronym
   * `AbPrDe`.
   */
  val SYMBOL_NAMES: List<String> = listOf(
    // short names
    "Ref", "Psi", "Url", "Key", "Tag", "Ide", "Job", "Pair", "Node", "Type", "Name", "Path", "File", "Icon", "Task",
    "Tree", "Scope", "Range", "Token", "Value", "Entry", "Cache", "Index", "Query", "Stub",

    // names that start with Solution
    "Solution", "SolutionModel", "SolutionElement", "SolutionSettings", "SolutionBuilder", "SolutionMarkModule",
    "SolutionExplorerNode", "SolutionLifetimes", "SolutionLoadStateTracker", "SolutionAnalysisService",
    "SolutionConfigurationManager", "SolutionWideSettingsStorage",

    // interfaces
    "ISolutionLoader", "ISolutionElement", "IProjectModelElement", "IDeclaredElement", "IPsiSourceFile", "ISymbolCache",
    "IReferenceProvider", "ITypeElement", "IExpressionType", "IDocumentMarkup", "ISearchDomain", "IHighlighting",
    "ICompletionItem", "IUnitTestElement", "IModuleReference", "ITextControl", "IThreadingModel", "IBackendDelegate",

    // names that hold Declaration
    "AbstractPropertyDeclarationHighlighter", "ClassDeclarationNavigator", "MethodDeclarationBuilder",
    "PropertyDeclarationVisitor", "FieldDeclarationOwner", "LocalVariableDeclaration", "TypeParameterDeclaration",
    "EnumMemberDeclarationStub", "DeclarationScopeProcessor", "DeclaredElementPresenter", "PartialDeclarationMerger",
    "NamespaceDeclarationTree", "DelegateDeclarationSignature", "EventDeclarationAccessor",

    // long camel names
    "CachedProjectModelElementContainerBuilder", "AsyncCompletionItemPresentationProvider",
    "DefaultReferenceResolveResultAccumulator", "IncrementalSymbolCacheInvalidationTracker",
    "RoslynWorkspaceSynchronizationService", "UnityAssetIndexingProgressReporter",
    "GlobalNavigationQuickSearchController", "StructuralSearchTemplateCompiler",
    "ConcurrentWeakValueIntObjectHashMap", "DaemonCodeAnalyzerStatusUpdater", "PsiDocumentManagerCommitListener",
    "ExternalAnnotationsCompletionContributor",

    // names that hold a digit
    "Http2Connection", "Utf8Decoder", "Base64Encoder", "Sha256Hasher", "Md5Checksum", "Ipv6Address", "Json5Parser",
    "CSharp11FeatureSet", "NetCore31Runtime", "Xaml2009Namespace", "Ecma262Regexp", "Log4jAppender", "Oauth2TokenStore",
    "Sqlite3Connection", "Tls13Handshake", "X509CertificateStore", "Vs2022Installation", "MsBuild17Project",

    // names that hold an underscore
    "symbol_table_entry", "psi_change_listener", "project_model_view", "my_cached_value", "resharper_host_settings",
    "unit_test_session_id", "DECLARATION_KEY", "MAX_CACHE_SIZE", "LOG_CATEGORY_NAME", "IDEA_HOME_PATH",

    // platform names
    "PsiElement", "PsiFileImpl", "VirtualFile", "LocalFileSystem", "DocumentImpl", "EditorFactory", "CodeStyleManager",
    "NameUtilCore", "MinusculeMatcher", "FuzzySearchProvider", "SearchEverywhereUI", "GotoFileModel",
    "ChooseByNameBase", "ContributorSearcher", "ActionManagerImpl", "ApplicationManager", "ProgressIndicator",
    "ModalityState", "WriteAction", "ReadAction", "Disposer", "LifetimeDefinition", "CoroutineScope",
    "DispatcherProvider", "ExtensionPointName", "ServiceContainer", "MessageBusConnection", "IndexableFilesIterator",
    "StubIndexExtension", "FileBasedIndexImpl", "TextAttributesKey", "HighlightSeverity", "InspectionProfileManager",
    "IntentionActionWrapper", "LookupElementBuilder", "TemplateActionContext", "UndoManagerImpl", "CommandProcessorEx",

    // ReSharper and Rider names
    "ReSharperHost", "RiderBackendService", "ResharperTestRunner", "CSharpTreeBuilder", "VBNetExpression",
    "TypeScriptSymbol", "RazorPageModel", "BlazorComponentTag", "NuGetPackageReference", "MsBuildTargetTable",
    "AssemblyResolutionContext", "DotNetRuntimeDetector", "ProjectModelViewHost", "SymbolTableFactory",
    "NamespaceQualifier", "GenericSubstitution", "ConstructorSignature", "ParameterListElement", "EnumeratorHostChecker",
    "UnityScriptComponent", "XamlPresentationBinding", "WebConfigTransformer", "EntityFrameworkMigration",
    "DebuggerEvaluationContext", "BreakpointRequestConverter", "SessionLifetimeTracker", "BackendUnitTestDiscoverer",
    "InlayHintPresentationFactory", "QuickFixAvailabilityChecker", "SweaTypeUsageCollector",

    // qualified names
    "JetBrains.ReSharper.Psi.CSharp.Tree.IClassDeclaration", "JetBrains.ReSharper.Psi.ISymbolTable",
    "JetBrains.Rider.Backend.Features.Navigation", "JetBrains.ProjectModel.ISolution",
    "com.intellij.psi.codeStyle.MinusculeMatcher", "com.intellij.openapi.editor.Document",
  )

  /**
   * Names outside the ASCII range.
   */
  val CYRILLIC_NAMES: List<String> = listOf(
    "Решение", "РешениеПроекта", "МодельРешения", "ЗагрузчикРешения", "Проект", "ФайлПроекта", "Документ",
    "МодельДокумента", "Символ", "ТаблицаСимволов", "Ссылка", "СсылкаНаТип", "Сборка", "ЗагрузчикСборки",
    "ПространствоИмен", "Объявление", "ОбъявлениеКласса", "ОбъявлениеМетода", "ОбъявлениеСвойства",
    "АбстрактноеОбъявление", "ОтложенноеОбъявлениеПеречисления", "Выражение", "ТипВыражения", "Оператор",
    "БлокОператоров", "Область", "ОбластьВидимости", "Кэш", "КэшСимволов", "Индекс", "ИндексФайлов", "Навигация",
    "ГлобальнаяНавигация",
  )
}
