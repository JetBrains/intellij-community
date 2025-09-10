package com.intellij.cce.report

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intellij.cce.core.Lookup
import com.intellij.cce.core.Session
import com.intellij.cce.evaluable.AIA_PROBLEMS
import com.intellij.cce.evaluation.data.*
import com.intellij.cce.metric.additionalList
import com.intellij.cce.workspace.info.FileEvaluationInfo
import com.intellij.cce.workspace.storages.FeaturesStorage
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import java.text.DecimalFormat
import kotlin.io.path.Path
import kotlin.io.path.name

class CardReportGenerator(
  featuresStorages: List<FeaturesStorage>,
  dirs: GeneratorDirectories,
  filterName: String,
  comparisonFilterName: String,
  private val layout: CardLayout,
  private val metrics: List<EvalMetric>,
) : FileReportGenerator(featuresStorages, dirs, filterName, comparisonFilterName) {

  override val scripts: List<Resource> = listOf(
    Resource("/diff.js", "../res/diff.js"),
    Resource("/highlight.js", "../res/highlight.js"),
    Resource("/customizable.js", "../res/customizable.js")
  )

  override fun getHtml(fileEvaluations: List<FileEvaluationInfo>, resourcePath: String, text: String): String {
    return createHTML().body {

      fileEvaluations.forEachIndexed { index, fileEvaluation ->
        textBlocks(fileEvaluation, index)
      }

      for (resource in scripts) {
        script { src = resource.destinationPath }
      }

      script {
        unsafe {
          fileEvaluations.forEachIndexed { index, fileEvaluation ->
            +"""${fileVariable(index)} = ${embedString(fileEvaluation.sessionsInfo.text)};"""
          }
        }
      }
    }
  }

  private fun BODY.textBlocks(fileEvaluation: FileEvaluationInfo, fileIndex: Int) {
    var sessionInfoIndex = 0

    for (session in fileEvaluation.sessionsInfo.sessions) {
      for (lookup in session.lookups) {
        renderLookup(fileEvaluation, fileIndex, session, lookup, sessionInfoIndex)
        sessionInfoIndex++
      }
    }
  }

  private fun BODY.renderLookup(
    fileEvaluation: FileEvaluationInfo,
    fileIndex: Int,
    session: Session,
    lookup: Lookup,
    index: Int,
  ) {
    val lookupIndex = session.lookups.indexOf(lookup)

    val props = DataProps(
      Path(fileEvaluation.sessionsInfo.filePath).name,
      fileEvaluation.sessionsInfo.text,
      session,
      lookup
    )

    val properties = determineProperties(props)

    val propertiesByCategory = properties.groupBy { it.category }
    val categories = propertiesByCategory.keys.sortedBy { it.priority }

    div {
      style = """
        border: 1px solid #ccc;
        padding: 16px;
        margin-bottom: 16px;
        border-radius: 8px;
        max-width: 100%;
        word-wrap: break-word;
        background-color: #f9f9f9;
        """.trimIndent()

      h3 {
        +"${index}. ${layout.name.placement.first(props)?.take(100)?.lines()?.first() ?: ""}"
      }

      layout.description.placement.first(props)?.let {
        pre("code") {
          style = """
                  overflow-x: auto;
                  max-width: 100%;
                  white-space: pre-wrap;
                  word-wrap: break-word;
              """.trimIndent()
          +it
        }
      }

      table {
        tr {
          for (category in categories) {
            th { +category.displayName }
          }
        }

        val tableSize = propertiesByCategory.map { it.value.size }.max()
        for (i in 0..tableSize) {
          tr {
            for (category in categories) {
              td {
                style = "padding-right: 3em;"
                val property = propertiesByCategory[category]?.getOrNull(i)
                if (property != null) {
                  renderProperty(property, fileIndex, session.id, lookupIndex)
                }
                else if (i == tableSize) {
                  popupContainer(popupId(session.id, lookupIndex, category))
                }
              }
            }
          }
        }
      }
    }
  }

  private fun <T> FlowOrPhrasingContent.renderProperty(
    property: ResolvedProperty<T>,
    fileIndex: Int,
    sessionId: String,
    lookupOrder: Int
  ) {
    val propertyValue = PropertyValue.build(property, fileIndex, sessionId, lookupOrder)

    val resultSuffix =
      if (property.suffix != null) ": ${property.suffix}"
      else if (propertyValue.inline != null) ": ${propertyValue.inline}"
      else ""

    a {
      style = ""

      if (property.hasProblems) {
        style += "color: red;"
      }

      if (propertyValue.popupOpenLogic != null) {
        style += "cursor: pointer;"
        style += "text-decoration: underline dashed;"
        onClick = propertyValue.popupOpenLogic
      }

      if (propertyValue.link != null) {
        style += "cursor: pointer;"
        href = propertyValue.link
        target = "_blank"
      }

      strong {
        style = "pointer-events: none;"

        +property.name
      }

      +resultSuffix

      if (propertyValue.popupOpenLogic != null) {
        +" ▼"
      }
    }
  }

  private fun FlowContent.popupContainer(popupId: String) {
    div {
      style = "position: relative;"

      div("popup-container") {
        id = popupId
        style = """
              position: absolute;
              border: 1px solid #d4d4d4;
              border-bottom: none;
              border-top: none;
              z-index: 99;
              top: 1.5em;
              left: 0;
              
              visibility: hidden;
            """.trimIndent()

        div("popup-container-header") {
          style = """
            display: flex;
            align-items: center;
            background-color: #e0e0e0;
            border: 1px solid #d4d4d4;
            border-bottom: none;
          """

          button {
            id = "${popupId}-copy-button"
            onClick = "copyPopupText('$popupId')"
            style = """
              cursor: pointer;
              margin: 4px;
            """
            +"Copy"
          }

          pre("popup-container-description") {
            id = "${popupId}-description"
            style = """
              margin: 4px;
            """
          }
        }

        div("popup-container-content") {
          id = "${popupId}-content"
          style = """
            width: 60em;
            max-height: 40em;
            min-height: 10em;
            background-color: lightgray;
            overflow: scroll;
          """.trimIndent()
        }
      }
    }
  }

  private fun determineProperties(props: DataProps): List<ResolvedProperty<*>> {
    return layout.presenters.flatMap { ResolvedProperty.fromData(it, props) } +
           metrics.filter { it.showInCard }.flatMap { ResolvedProperty.fromMetric(it, props) }
  }
}

private fun fileVariable(fileIndex: Int): String = "fileText${fileIndex}"

private fun popupId(sessionId: String, lookupIndex: Int, category: PresentationCategory): String =
  "${sessionId}-${lookupIndex}-${category.name}"

private fun embedString(string: String): String = "pako.ungzip(atob(`${zipString(string)}`), { to: 'string' })"

private data class ResolvedProperty<T>(
  val name: String,
  val suffix: String?,
  val category: PresentationCategory,
  val placement: DataPlacement<*, T>?,
  val placementIndex: Int,
  val value: T?,
  val renderer: DataRenderer<T>?,
  val hasProblems: Boolean = false,
) {
  companion object {
    fun <T> fromData(presenter: EvalDataPresenter<T>, props: DataProps): List<ResolvedProperty<T>> {
      val resolved = presenter.data.placement.restore(props)
        .filter { presenter.presentation.renderer?.skip(it) != true }
        .mapIndexed { index, value ->
          val problems = props.lookup.additionalList(AIA_PROBLEMS) ?: emptyList()
          ResolvedProperty(
            name = presenter.presentation.dynamicName?.resolve(props, value) ?: presenter.data.name,
            placement = presenter.data.placement,
            placementIndex = index,
            category = presenter.presentation.category,
            suffix = null,
            value = value,
            renderer = presenter.presentation.renderer,
            hasProblems = problems.contains(presenter.data.valueId(index))
          )
        }

      if (resolved.isEmpty() && !presenter.presentation.ignoreMissingData) {
        val unresolved = ResolvedProperty(
          name = presenter.data.name,
          suffix = "missing",
          category = presenter.presentation.category,
          placement = presenter.data.placement,
          placementIndex = -1,
          value = null,
          renderer = null,
          hasProblems = false
        )

        return listOf(unresolved)
      }

      return resolved
    }

    fun fromMetric(metric: EvalMetric, props: DataProps): List<ResolvedProperty<*>> =
      listOf(resolve(metric, props, metric.dependencies))

    private fun <T> resolve(metric: EvalMetric, props: DataProps, dependencies: MetricDependencies<T>): ResolvedProperty<T> {
      val score = metric.calculateScore(props)
      return ResolvedProperty(
        metric.name,
        suffix = DecimalFormat("#.##").format(score),
        category = PresentationCategory.METRIC,
        placement = null,
        placementIndex = -1,
        dependencies.renderableValue(props),
        dependencies.renderer,
        hasProblems = metric.hasProblem(score)
      )
    }
  }
}

private data class PropertyValue(
  val popupOpenLogic: String?,
  val inline: String?,
  val link: String? = null
) {
  companion object {
    fun <T> build(
      property: ResolvedProperty<T>,
      fileIndex: Int,
      sessionId: String,
      lookupIndex: Int,
    ): PropertyValue {
      if (property.value == null) {
        return PropertyValue(null, null)
      }

      if (property.renderer == null) {
        return PropertyValue(null, null)
      }

      val element = "'${popupId(sessionId, lookupIndex, property.category)}'"
      val stringValues =
        if (property.placement != null) nativeTexts(property.placement, fileIndex, sessionId, lookupIndex, property.placementIndex)
        else embeddedTexts(property.renderer, property.value)

      val description = if (property.value is HasDescription) "'${property.value.descriptionText}'" else "null"

      return when (property.renderer) {
        is DataRenderer.InlineBoolean -> PropertyValue(null, "${property.value}")
        is DataRenderer.InlineLong -> PropertyValue(null, "${property.value}")
        is DataRenderer.InlineDouble -> PropertyValue(null, "${property.value}")
        is DataRenderer.InlineInt -> PropertyValue(null, "${property.value}")
        is DataRenderer.CodeCommentRanges -> PropertyValue(null, "${property.value}")
        is DataRenderer.ClickableLink -> PropertyValue(null, null, "${property.value}")
        is DataRenderer.Text -> PropertyValue("""openText($element, ${stringValues[0]}, ${description}, ${property.renderer.wrapping});""", null)
        is DataRenderer.Lines -> PropertyValue("""openText($element, ${stringValues[0]}, ${description});""", null)
        is DataRenderer.TextDiff -> PropertyValue("""openDiff($element, ${stringValues[0]}, ${stringValues[1]}, ${description});""", null)
        is DataRenderer.Snippets -> PropertyValue("""openSnippets($element, ${stringValues[0]});""", inline = null)
        is DataRenderer.ColoredInsights -> PropertyValue("""openText($element, ${stringValues[0]}, ${description});""", null)
      }
    }

    private fun <T> nativeTexts(
      placement: DataPlacement<*, T>,
      fileIndex: Int,
      sessionId: String,
      lookupIndex: Int,
      placementIndex: Int,
    ): List<String> {
      return when (placement) {
        is DataPlacement.AdditionalBoolean -> listOf(
          """sessions["${sessionId}"]["_lookups"][${lookupIndex}]["additionalInfo"]["${placement.propertyKey}"].toString()"""
        )
        is DataPlacement.AdditionalDouble -> listOf(
          """sessions["${sessionId}"]["_lookups"][${lookupIndex}]["additionalInfo"]["${placement.propertyKey}"].toString()"""
        )
        is DataPlacement.AdditionalInt -> listOf(
          """sessions["${sessionId}"]["_lookups"][${lookupIndex}]["additionalInfo"]["${placement.propertyKey}"].toString()"""
        )
        is DataPlacement.Latency -> listOf(
          """sessions["${sessionId}"]["_lookups"][${lookupIndex}]["latency"].toString()"""
        )
        is DataPlacement.AdditionalText -> listOf(
          """sessions["${sessionId}"]["_lookups"][${lookupIndex}]["additionalInfo"]["${placement.propertyKey}"]"""
        )
        is DataPlacement.AdditionalConcatenatedLines -> listOf(
          """sessions["${sessionId}"]["_lookups"][${lookupIndex}]["additionalInfo"]["${placement.propertyKey}"].split("\n").map(l => "• " + l).join("\n")"""
        )
        is DataPlacement.AdditionalJsonSerializedStrings -> listOf(
          """JSON.parse(sessions["${sessionId}"]["_lookups"][${lookupIndex}]["additionalInfo"]["${placement.propertyKey}"])"""
        )
        is DataPlacement.CurrentFileUpdate -> listOf(
          fileVariable(fileIndex),
          """sessions["${sessionId}"]["_lookups"][${lookupIndex}].suggestions[0].presentationText"""
        )
        is DataPlacement.FileUpdates -> listOf(
          """sessions["${sessionId}"]["_lookups"][${lookupIndex}]["additionalInfo"]["${placement.propertyKey}"][${placementIndex}].originalText""",
          """sessions["${sessionId}"]["_lookups"][${lookupIndex}]["additionalInfo"]["${placement.propertyKey}"][${placementIndex}].updatedText"""
        )
        is DataPlacement.AdditionalCodeCommentRanges -> listOf(
          """sessions["${sessionId}"]["_lookups"][${lookupIndex}]["additionalInfo"]["${placement.propertyKey}"][${placementIndex}].start""",
          """sessions["${sessionId}"]["_lookups"][${lookupIndex}]["additionalInfo"]["${placement.propertyKey}"][${placementIndex}].end""",
          """sessions["${sessionId}"]["_lookups"][${lookupIndex}]["additionalInfo"]["${placement.propertyKey}"][${placementIndex}].text""",
          """sessions["${sessionId}"]["_lookups"][${lookupIndex}]["additionalInfo"]["${placement.propertyKey}"][${placementIndex}].negativeExample""",
          """sessions["${sessionId}"]["_lookups"][${lookupIndex}]["additionalInfo"]["${placement.propertyKey}"][${placementIndex}].category""",
        )
        is DataPlacement.ColoredInsightsPlacement -> listOf(
          """sessions["${sessionId}"]["_lookups"][${lookupIndex}]["additionalInfo"]["${placement.propertyKey}"][${placementIndex}].text"""
        )
      }
    }

    private fun <T> embeddedTexts(renderer: DataRenderer<T>, value: T): List<String> {
      return when (renderer) {
        DataRenderer.InlineBoolean -> listOf("\"${value}\"")
        DataRenderer.InlineLong -> listOf("\"${value}\"")
        DataRenderer.InlineDouble -> listOf("\"${value}\"")
        DataRenderer.InlineInt -> listOf("\"${value}\"")
        DataRenderer.ClickableLink -> listOf()
        is DataRenderer.Text -> listOf(embedString(value as String))
        DataRenderer.Lines -> listOf(embedString((value as List<*>).joinToString("\n") { "• $it" }))
        DataRenderer.Snippets -> (value as List<*>).map { embedString(it as String) }
        DataRenderer.TextDiff -> listOf(
          embedString((value as TextUpdate).originalText),
          embedString((value as TextUpdate).updatedText)
        )
        DataRenderer.CodeCommentRanges -> listOf(
          "\"${(value as CodeCommentRange).start}\"",
          "\"${(value as CodeCommentRange).end}\"",
          embedString((value as CodeCommentRange).text),
          "\"${(value as CodeCommentRange).negativeExample}\"",
          embedString((value as CodeCommentRange).category.toString()),
        )
        DataRenderer.ColoredInsights -> listOf(embedString((value as ColoredInsightsData).text))
      }
    }
  }
}

data class CardLayout(
  val name: EvalData<String>,
  val description: EvalData<String>,
  val presenters: List<EvalDataPresenter<*>>,
) {
  companion object {
    val gson: Gson = run {
      val builder = GsonBuilder()

      builder.registerTypeAdapter(DataPlacement::class.java, DataPlacement.Serializer())
      builder.registerTypeAdapter(DataRenderer::class.java, DataRenderer.Serializer())
      builder.registerTypeAdapter(DynamicName::class.java, DynamicName.Serializer())

      builder.create()
    }
  }
}