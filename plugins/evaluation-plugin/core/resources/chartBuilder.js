function createChartCanvas(title) {
  const existingContainer = document.getElementById(`${title}Container`)
  if (existingContainer) {
    existingContainer.remove()
  }

  const chartContainer = document.createElement('div')
  chartContainer.id = `${title}Container`
  chartContainer.className = 'chartContainer'

  document.body.appendChild(chartContainer)

  const existingCanvas = document.getElementById(`${title}ID`)
  if (existingCanvas) {
    existingCanvas.remove()
  }

  const canvas = document.createElement('canvas')
  canvas.id = `${title}ID`
  canvas.className = 'canvasContainer'
  chartContainer.appendChild(canvas)

  return canvas
}

function syncChartLegends(chartList) {
  chartList.forEach(chart => {
    chart.options.plugins.legend.onClick = (event, legendItem) => {
      const datasetIndex = legendItem.datasetIndex
      chartList.forEach(chartInstance => {
        const dataset = chartInstance.data.datasets[datasetIndex]
        if (dataset) {
          dataset.hidden = !dataset.hidden
        }
        chartInstance.update()
      })
    }
  })
}

function createChart(title, chartList, showLegend = false) {
  const chartContext = createChartCanvas(title).getContext('2d')

  const chart = new Chart(chartContext, {
    type: 'line',
    title: title,
    data: {
      labels: [],
      datasets: [],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      scales: {
        x: {
          title: {
            display: true,
          },
        },
        y: {
          title: {
            display: true,
          },
          beginAtZero: true,
        },
      },
      plugins: {
        legend: {
          display: showLegend,
          position: 'left',
        },
      },
    },
  })

  syncChartLegends(chartList)

  return chart
}

function populateChart(chart, unfilteredDatasets, filterFn = (name) => true) {
  const metricNames = table
    .getColumns()
    .filter(column => column.getVisibility())
    .filter(name => filterFn(name._column.parent.definition?.title ?? name.getField()))
    .map(column => column.getField())
    .filter(field => field !== 'id' && field !== 'file')

  const datasets = unfilteredDatasets.map(dataset => {
    const filteredData = dataset.data
      .filter(item => metricNames.includes(item.field))
      .map(item => item.value)
    return {
      ...dataset,
      data: filteredData,
    }
  })

  chart.data.labels = table
    .getColumns()
    .filter(column => metricNames.includes(column.getField()))
    .map(column => column._column.parent.definition?.title ?? column.getField())

  if (metricNames.length === 1) {
    chart.data.labels = ['', ...chart.data.labels, '']
    datasets.forEach(dataset => {
      dataset.data = [null, ...dataset.data, null]
    })
  }

  chart.data.datasets = datasets
  chart.update()
}

function fetchUnfilteredDatasets() {
  return table.getData().map(entry => {
    const allMetricValues = table
      .getColumns()
      .map(column => {
        const field = column.getField()
        return {field: field, value: parseFloat(entry[field]) || 0}
      })

    const randomRed = Math.floor(Math.random() * 194)
    const randomGreen = Math.floor(Math.random() * 194)
    const randomBlue = Math.floor(Math.random() * 194)
    const transparency = '0.6'
    const summaryColor = `rgba(0, 128, 0, 1)`
    const randomColor = `rgba(${randomRed}, ${randomGreen}, ${randomBlue}, ${transparency})`
    const isSummary = entry.file.match(/summary/i)
    return {
      label: entry.file.match(/<a href='files\/(.*?)\.html/)?.[1] ?? entry.file,
      data: allMetricValues,
      borderColor: isSummary ? summaryColor : randomColor,
      backgroundColor: isSummary ? summaryColor : randomColor,
      borderWidth: isSummary ? 2 : 1,
      borderDash: isSummary ? [5, 5] : [],
      tension: 0.4, //smooth lines
    }
  })
}

function setYAxisLimits(chart) {
  chart.options.scales.y = {
    ...chart.options.scales.y,
    min: 0,
    max: 1,
  }
}

function filterOutSummaryMetric(unfilteredDatasets) {
  return unfilteredDatasets.filter(dataset => !dataset.label.match(/summary/i))
}

function redrawCharts() {
  if (!enableMetricsCharts) return
  chartList = []
  const unfilteredDatasets = fetchUnfilteredDatasets()

  smallMetricsChart = createChart("SmallMetrics", chartList, true)
  setYAxisLimits(smallMetricsChart)
  chartList.push(smallMetricsChart)

  latencyMetricsChart = createChart("LatencyMetrics", chartList, false)
  chartList.push(latencyMetricsChart)

  lineMetricsChart = createChart("LineMetrics", chartList, false)
  chartList.push(lineMetricsChart)

  populateChart(smallMetricsChart, unfilteredDatasets,
    name => !name.includes("FilterReason") && !name.includes("Latency") &&
      !["Lines", "Positions", "Suggestions", "FirstPerfectLineSession", "MatchedLineLength"].includes(name)
  )

  populateChart(latencyMetricsChart, unfilteredDatasets, name => name.includes("Latency"))

  populateChart(lineMetricsChart, filterOutSummaryMetric(unfilteredDatasets),
    name => ["Lines", "Positions", "Suggestions", "FirstPerfectLineSession", "MatchedLineLength"].includes(name) || name.includes("FilterReason")
  )
}

function createSankeyChart(title) {
  const chartContext = createChartCanvas(title).getContext('2d')

  const chart = new Chart(chartContext, {
    type: 'sankey',
    data: {
      datasets: [],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      layout: {
        padding: {
          top: 50,
          bottom: 50,
        },
      },
    },
  })

  return chart
}

function redrawSankeyChart() {
  if (!enableSankeyChart) return
  filterReasonSankeyChart = createSankeyChart("FilterReasons")

  populateSankeyChart(filterReasonSankeyChart, sankeyChartStructure)
}

function getValueOfMetricsFromTabulatorTable(name) {
  return table
    .getColumns()
    .filter(column => {
      if ((column._column.parent.definition?.title ?? column.getField()) === name) return true
    })
    .map(column => {
      metricName = column._column.parent.definition?.headerTooltip ?? column.getField()
      metricValue = parseInt(column._column.cells[0].value)
      return {
        to: metricName,
        flow: metricValue,
      }
    })
    .filter(column => column.flow !== undefined && column.flow !== 0)
}

function generateSankeyEdges(structure) {
  if (structure.children === undefined) //it is leaf
    return getValueOfMetricsFromTabulatorTable(structure)

  let metricSum = 0
  let sankeyData = []

  if (structure.hasOwnProperty("lambda"))
    metricSum = getValueOfMetricsFromTabulatorTable(structure.lambda)
      .reduce((sum, column) => sum + column.flow, 0)

  if (structure.children) {
    structure.children.forEach(child => {
      let childMetrics = generateSankeyEdges(child)

      if (child.children === undefined) { //child is a leaf
        metricSum -= childMetrics.reduce((acc, metric) => acc + metric.flow, 0)
        sankeyData.push(...childMetrics.map(metric => {
          return {
            from: structure.head,
            to: metric.to,
            flow: metric.flow,
          }
        }))
      }
      else {
        const childrenNames = structure.children.map(child => child.head)
        const childSum = childMetrics.filter(child => childrenNames.includes(child.from)).reduce((acc, metric) => acc + metric.flow, 0)
        metricSum -= childSum

        sankeyData.push(...childMetrics)
        if (childSum !== 0) {
          sankeyData.push({
            from: structure.head,
            to: child.head,
            flow: childSum,
          })
        }
      }
    })
  }

  if (structure.hasOwnProperty("lambda") && metricSum !== 0) {
    sankeyData.push({
      from: structure.head,
      to: structure.underFlow,
      flow: metricSum,
    })
  }

  return sankeyData
}

function populateSankeyChart(chart, structure) {
  const sankeyData = generateSankeyEdges(structure)

  chart.data.datasets = [{
    data: sankeyData,
    borderWidth: 0,
    nodePadding: 45,
    nodeWidth: 3,
    priority: {},
    colorFrom: (c) => getColorFrom(c.dataset.data[c.dataIndex].from),
    colorTo: (c) => getColorTo(c.dataset.data[c.dataIndex].to),
  }]

  chart.update()
}

function getColorFrom(name) {
  const colorsFrom = {
    "correct suggestions": "rgba(0, 128, 0, 1)",
    "incorrect suggestions": "rgba(128, 0, 0, 1)",
  }
  return colorsFrom[name] || "rgba(40, 40, 128, 1)"
}

function getColorTo(name) {
  const colorsTo = {
    "correct suggestions": "rgba(0, 128, 0, 1)",
    "incorrect suggestions": "rgba(128, 0, 0, 1)",
  }
  return colorsTo[name] || "rgba(120, 120, 160, 1)"
}

const enableMetricsCharts = false
const enableSankeyChart = true

document.addEventListener("DOMContentLoaded", () => {
  redrawCharts()
  redrawSankeyChart()
})