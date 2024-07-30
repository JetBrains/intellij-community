// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.report

import com.intellij.cce.metric.MetricInfo
import com.intellij.cce.metric.MetricValueType
import com.intellij.cce.workspace.info.FileErrorInfo
import com.intellij.cce.workspace.info.FileEvaluationInfo
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import java.io.BufferedInputStream
import java.io.File
import java.io.FileWriter
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*
import kotlin.io.path.writeText

class HtmlReportGenerator(
  private val dirs: GeneratorDirectories,
  private val defaultMetrics: List<String>?,
  private val fileGenerator: FileReportGenerator
) : FullReportGenerator {
  companion object {
    private const val globalReportName = "index.html"

    private val commonResources = listOf(
      "/style.css",
      "/pako.min.js",
      "/tabulator.min.js",
      "/tabulator.min.css",
      "/tabulator.min.css.map",
      "/error.js",
      "/options.css",
      "/fonts/JetBrainsMono-Medium.eot",
      "/fonts/JetBrainsMono-Medium.woff"
    )
    private const val diffColumnTitle = "diff"
  }

  override val type: String = "html"

  private val errorReferences: MutableMap<String, Path> = mutableMapOf()

  private fun copyResources(resource: String) {
    val resultFile = Paths.get(dirs.resourcesDir.toString(), resource).toFile()
    resultFile.parentFile.mkdirs()
    Files.copy(HtmlReportGenerator::class.java.getResourceAsStream(resource)!!, resultFile.toPath())
  }

  init {
    fileGenerator.scripts.forEach { copyResources(it.sourcePath) }
    commonResources.forEach { copyResources(it) }
  }

  override fun generateFileReport(sessions: List<FileEvaluationInfo>) = fileGenerator.generateFileReport(sessions)

  override fun generateErrorReports(errors: List<FileErrorInfo>) {
    for (fileError in errors) {
      val filePath = Paths.get(fileError.path)
      val reportPath = dirs.getPaths(filePath.fileName.toString()).reportPath
      val reportTitle = "Error on actions generation for file ${filePath.fileName}"
      createHTML().html {
        head {
          title(reportTitle)
        }
        body {
          h1 { +reportTitle }
          h2 { +"Message" }
          pre { code { +fileError.message } }
          h2 {
            +"Stacktrace "
            button {
              id = "copyBtn"
              unsafe { raw("&#128203") }
            }
          }
          pre {
            code {
              id = "stackTrace"
              +fileError.stackTrace
            }
          }
          script { src = "../res/error.js" }
        }
      }.also { html -> FileWriter(reportPath.toString()).use { it.write(html) } }
      errorReferences[filePath.toString()] = reportPath
    }
  }

  override fun generateGlobalReport(globalMetrics: List<MetricInfo>): Path {
    val reportPath = Paths.get(dirs.filterDir.toString(), globalReportName)

    val reportTitle = "Evaluation report"
    createHTML().html {
      head {
        title(reportTitle)
        meta { charset = "utf-8" }
        script { src = "res/tabulator.min.js" }
        link {
          href = "res/tabulator.min.css"
          rel = "stylesheet"
        }
        link {
          href = "res/options.css"
          rel = "stylesheet"
        }
      }
      body {
        h1 { +reportTitle }
        h3 { +"${fileGenerator.reportReferences.size} file(s) successfully processed" }
        h3 { +"${errorReferences.size} errors occurred" }
        unsafe { raw(getToolbar(globalMetrics)) }
        div { id = "metricsTable" }
        script { unsafe { raw(getMetricsTable(globalMetrics)) } }
      }
    }.also { html -> reportPath.writeText(html) }

    return reportPath
  }

  private fun getMetricsTable(globalMetrics: List<MetricInfo>): String {
    val evaluationTypes = globalMetrics.map { it.evaluationType }.toSet().sorted().toMutableList()
    val uniqueMetricsInfo = globalMetrics.filter { it.evaluationType == evaluationTypes.first() }
    val manyTypes = (evaluationTypes.size > 1)
    val withDiff = (evaluationTypes.size == 2)
    if (withDiff) evaluationTypes.add(diffColumnTitle)
    var rowId = 1

    val errorMetrics = globalMetrics.map {
      MetricInfo(it.name, it.description, Double.NaN, null, it.evaluationType, it.valueType, it.showByDefault)
    }

    fun getReportMetrics(repRef: ReferenceInfo) = globalMetrics.map { metric ->
      val refMetric = repRef.metrics.find { it.name == metric.name && it.evaluationType == metric.evaluationType }
      MetricInfo(
        metric.name,
        metric.description,
        refMetric?.value ?: Double.NaN,
        refMetric?.confidenceInterval,
        metric.evaluationType,
        metric.valueType,
        metric.showByDefault
      )
    }

    fun formatMetrics(metrics: List<MetricInfo>): String = (
      if (withDiff) listOf(metrics, metrics
        .groupBy({ it.name }, { Triple(it.value, it.valueType, it.showByDefault) })
        .mapValues { with(it.value) { Triple(first().first - last().first, first().second, first().third) } }
        .map { MetricInfo(it.key, "", it.value.first, null, diffColumnTitle, it.value.second, it.value.third) }).flatten()
      else metrics
                                                           ).joinToString(",") {
        "${it.name}${it.evaluationType}:'${
          formatMetricValue(it.value, it.confidenceInterval, it.valueType)
        }'"
      }

    fun getErrorRow(errRef: Map.Entry<String, Path>): String =
      "{id:${rowId++},file:${getErrorLink(errRef)},${formatMetrics(errorMetrics)}}"

    fun getReportRow(repRef: Map.Entry<String, ReferenceInfo>) =
      "{id:${rowId++},file:${getReportLink(repRef)},${formatMetrics(getReportMetrics(repRef.value))}}"

    return """
        |let tableData = [{id:0,file:'Summary',${formatMetrics(globalMetrics)}}
        |${with(errorReferences) { if (isNotEmpty()) map { getErrorRow(it) }.joinToString(",\n", ",") else "" }}
        |${with(fileGenerator.reportReferences) { if (isNotEmpty()) map { getReportRow(it) }.joinToString(",\n", ",") else "" }}];
        |let table=new Tabulator('#metricsTable',{data:tableData,
        |columns:[{title:'File Report',field:'file',formatter:'html'${if (manyTypes) ",width:'120'" else ""}},
        |${
      uniqueMetricsInfo.joinToString(",\n") { metric ->
        "{title:'${metric.name}',headerTooltip:'${metric.description}',visible:${metric.visible()},columns:[${
          evaluationTypes.joinToString(",") { type ->
            "{title:'$type',field:'${metric.name.filter { it.isLetterOrDigit() }}$type',sorter:'number',align:'right',headerVertical:${manyTypes},visible:${metric.visible()}}"
          }
        }]}"
      }
    }],
        |layout:'fitColumns',pagination:'local',paginationSize:25,paginationSizeSelector:true,movableColumns:true,
        |dataLoaded:function(){this.getRows()[0].freeze();this.setFilter(myFilter)}});
        """.trimMargin()
  }

  private fun MetricInfo.visible(): Boolean = if (defaultMetrics != null) name in defaultMetrics else showByDefault

  private fun formatMetricValue(value: Double, confidenceInterval: Pair<Double, Double>?, type: MetricValueType): String = when {
    value.isNaN() -> "—"
    type == MetricValueType.INT -> "${value.toInt()}" + (confidenceInterval?.let {
      " (${confidenceInterval.first.toInt()}; ${confidenceInterval.second.toInt()})"
    } ?: "")
    type == MetricValueType.DOUBLE -> value.format() + (confidenceInterval?.let {
      " (${confidenceInterval.first.format()}; ${confidenceInterval.second.format()})"
    } ?: "")
    else -> throw IllegalArgumentException("Unknown metric value type")
  }

  private fun Double.format() = "%.3f".format(Locale.US, this)

  private fun getErrorLink(errRef: Map.Entry<String, Path>): String =
    "\"<a href='${getHtmlRelativePath(dirs.filterDir, errRef.value)}' class='errRef' target='_blank'>${
      Paths.get(errRef.key).fileName
    }</a>\""

  private fun getReportLink(repRef: Map.Entry<String, ReferenceInfo>): String =
    "\"<a href='${getHtmlRelativePath(dirs.filterDir, repRef.value.pathToReport)}' target='_blank'>${repRef.key}</a>\""

  private fun getHtmlRelativePath(base: Path, path: Path): String {
    return base.relativize(path).toString().replace(File.separatorChar, '/')
  }

  private fun getToolbar(globalMetrics: List<MetricInfo>): String {
    val evaluationTypes = globalMetrics.mapTo(HashSet()) { it.evaluationType }
    val uniqueMetricsInfo = globalMetrics.filter { it.evaluationType == evaluationTypes.first() }
    val withDiff = evaluationTypes.size == 2
    if (withDiff) evaluationTypes.add(diffColumnTitle)
    val sessionMetricIsPresent = globalMetrics.any { it.name == "Sessions" }
    val toolbar = createHTML().div {
      div("toolbar") {
        input(InputType.text) {
          id = "search"
          placeholder = "Search"
          maxLength = "50"
        }
      }
      div("toolbar") {
        button(classes = "toolbarBtn") {
          id = "dropdownBtn"
          +"Metrics visibility"
        }
        ul("dropdown") {
          uniqueMetricsInfo.map { metric ->
            li {
              input(InputType.checkBox) {
                id = metric.name.filter { it.isLetterOrDigit() }
                checked = metric.visible()
                onClick = "updateCols()"
                +metric.name
              }
            }
          }
        }
      }
      div("toolbar") {
        button(classes = "toolbarBtn") {
          id = "redrawBtn"
          +"Redraw table"
        }
      }
      if (sessionMetricIsPresent) div("toolbar") {
        button(classes = "toolbarBtn") {
          id = "emptyRowsBtn"
          +"Show empty rows"
        }
      }
      if (withDiff) div("toolbar") {
        button(classes = "toolbarBtn active") {
          id = "diffBtn"
          +"Hide diff"
        }
      }

    }
    val ifDiff: (String) -> String = { if (withDiff) it else "" }
    val ifSessions: (String) -> String = { if (sessionMetricIsPresent) it else "" }
    val filteredNames = uniqueMetricsInfo.map { it.name.filter { ch -> ch.isLetterOrDigit() } }
    val toolbarScript = """|<script>
        |${filteredNames.joinToString("") { "let ${it}=document.getElementById('${it}');" }}
        |function updateCols(${ifDiff("toggleDiff=false")}){
        |${ifDiff("if(toggleDiff)diffBtn.classList.toggle('active');diffBtn.textContent=diffHidden()?'Show diff':'Hide diff';")}
        ${
      filteredNames.joinToString("\n") { metric ->
        """
            ||if(${metric}.checked){${evaluationTypes.joinToString("") { type -> "table.showColumn('${metric}${type}');" }}
            ||${ifDiff("if (diffHidden())table.hideColumn('${metric}$diffColumnTitle');")}}
            ||else{${evaluationTypes.joinToString("") { type -> "table.hideColumn('${metric}${type}');" }}}
            """.trimMargin()
      }
    }}
        |function toggleColumn(name){${evaluationTypes.joinToString("") { "table.toggleColumn(name+'$it');" }}}
        |let search=document.getElementById('search');search.oninput=()=>table.setFilter(myFilter);
        |let redrawBtn=document.getElementById('redrawBtn');redrawBtn.onclick=()=>table.redraw();
        ${
      ifDiff("""
            ||let diffBtn=document.getElementById('diffBtn');
            ||diffBtn.onclick=()=>updateCols(true);
            ||let diffHidden=()=>!diffBtn.classList.contains('active');
            """.trimMargin())
    }
        ${
      ifSessions("""
            ||let emptyRowsBtn=document.getElementById('emptyRowsBtn');emptyRowsBtn.onclick=()=>toggleEmptyRows();
            ||let emptyHidden=()=>!emptyRowsBtn.classList.contains('active');
            ||function toggleEmptyRows(){if(emptyHidden()){
            ||emptyRowsBtn.classList.add('active');emptyRowsBtn.textContent='Hide empty rows'}
            ||else{emptyRowsBtn.classList.remove('active');emptyRowsBtn.textContent='Show empty rows'}
            ||table.setFilter(myFilter)}
            ||let toNum=(str)=>isNaN(+str)?0:+str;
            """.trimMargin())
    }
        |let myFilter=(data)=>(new RegExp(`.*${'$'}{search.value}.*`,'i')).test(data.file)
        ${ifSessions("|&&Math.max(${evaluationTypes.joinToString { "toNum(data['Sessions$it'])" }})>-!emptyHidden();")}
        |</script>""".trimMargin()
    return toolbar + toolbarScript
  }

  private fun downloadV2WebFiles() {
    val resultFile = File(dirs.resourcesDir.toString())

    Files.copy(
      BufferedInputStream(URL("https://packages.jetbrains.team/files/p/ccrm/completion-golf-web/index.js").openStream()),
      resultFile.resolve("index-v2.js").toPath(),
      StandardCopyOption.REPLACE_EXISTING
    )
    Files.copy(
      BufferedInputStream(URL("https://packages.jetbrains.team/files/p/ccrm/completion-golf-web/index.css").openStream()),
      resultFile.resolve("index-v2.css").toPath(),
      StandardCopyOption.REPLACE_EXISTING
    )
  }
}
