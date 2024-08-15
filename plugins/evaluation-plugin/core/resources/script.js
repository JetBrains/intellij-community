const favoriteFeatures = getFavoriteFeaturesFromCookie()
isCompletionGolf = false

const prefix = "ep@"
const LC_KEYS = {
  delimiter: prefix + "delimiter"
};


document.addEventListener("click", function (e) {
  if (e.target.closest(".multiline") != null) {
    updateMultilinePopup(e)
    return
  }
  const suggestionDiv = e.target.closest(".suggestion")
  const featureValueDiv = e.target.closest(".feature-value")
  if (featureValueDiv != null) {
    e.stopPropagation()
    if (e.target.classList.contains("favorite-button")) {
      if (e.target.classList.contains("in-favorite")) {
        e.target.classList.remove("in-favorite")
      }
      else {
        e.target.classList.add("in-favorite")
      }
    }
  }
  else if (e.target.classList.contains("session")) {
    updatePopup(e.target)
  }
  else if (suggestionDiv != null) {
    updateElementFeatures(suggestionDiv)
  }
  else {
    closeAllLists()
  }
})

function updateBackgrounds(e, elementClasses, bgClass) {
  let selected = e.target.selectedOptions[0].value
  for (const clazz of elementClasses.filter(val => val !== selected)) {
    removeClassForElements(clazz, bgClass, false)
  }
  addClassForElements(selected, bgClass, true)
}

document.getElementById("wrong-filters").onchange = (e) => updateBackgrounds(e,  ["raw-filter", "analyzed-filter"], "bg-filters-skipped")
document.getElementById("model-skipped").onchange = (e) => updateBackgrounds(e, ["trigger-skipped", "filter-skipped"], "bg-model-skipped")

function removeClassForElements(elementsClassName, classToAdd) {
  let tokens = document.getElementsByClassName(elementsClassName)
  for (const token of tokens) {
    token.classList.remove(classToAdd)
  }
}

function addClassForElements(elementClass, classToAdd) {
  let tokens = document.getElementsByClassName(elementClass)
  for (const token of tokens) {
    token.classList.add(classToAdd)
  }
}

function closeAllLists() {
  const autocompleteDivs = document.getElementsByClassName("autocomplete-items")
  Array.from(autocompleteDivs).forEach((div) => {
    div.parentNode.removeChild(div)
  })
  const feateresDivs = document.getElementsByClassName("suggestions")
  Array.from(feateresDivs).forEach((div) => {
    div.classList.remove("suggestions")
  })
}

function changeLookupOrder() {
  const lookupOrderInput = document.getElementById("lookup-order")
  const lookupOrder = lookupOrderInput != null ? lookupOrderInput.value : 0
  const codeContainers = document.getElementsByClassName("code-container")
  for (let i = 0; i < codeContainers.length; i++) {
    if (lookupOrder != i) {
      codeContainers[i].classList.add("order-hidden")
    }
    else {
      codeContainers[i].classList.remove("order-hidden")
    }
  }
}

function selectFavoriteFeature(type, name, element) {
  if (type in favoriteFeatures && favoriteFeatures[type].includes(name)) {
    favoriteFeatures[type] = favoriteFeatures[type].filter(it => it !== name)
  }
  else {
    if (!(type in favoriteFeatures)) {
      favoriteFeatures[type] = []
    }
    favoriteFeatures[type].push(name)
  }
  saveFavoriteFeaturesToCookie()
}

function getLookup(sessionDiv) {
  if (isCompletionGolf) {
    const sessionId = sessionDiv.id.split(" ")[0]
    const lookups = sessions[sessionId]["_lookups"]
    return lookups[sessionDiv.dataset.cl]
  }
  else {
    const parts = sessionDiv.id.split(" ")
    const sessionId = parts[0]
    const lookups = sessions[sessionId]["_lookups"]
    const lookupOrder = parts[1]
    if (lookups.length <= lookupOrder) return
    return lookups[lookupOrder]
  }
}

function updatePopup(sessionDiv) {
  const lookup = getLookup(sessionDiv)
  const popup = document.createElement("DIV")
  popup.setAttribute("class", "autocomplete-items")
  const prefixDiv = document.createElement("DIV")
  prefixDiv.setAttribute("style", "background-color: lightgrey;")
  const codeElement = document.querySelector('.code');
  if ("aia_user_prompt" in lookup["additionalInfo"]) {
    prefixDiv.textContent = `user prompt: "${lookup["additionalInfo"]["aia_user_prompt"]}"; latency: ${lookup["latency"]}`
  } else {
    prefixDiv.textContent = `prefix: "${lookup["prefix"]}"; latency: ${lookup["latency"]}`
  }
  popup.appendChild(prefixDiv)
  // order: () -> (suggestions or diffView) -> features -> contexts
  const needAddFeatures = sessionDiv.classList.contains("diffView") || sessionDiv.classList.contains("suggestions")
  const needAddContext = sessionDiv.classList.contains("features")
  const isCodeGeneration = sessionDiv.classList.contains("code-generation");
  closeAllLists()
  if (needAddFeatures) {
    addCommonFeatures(sessionDiv, popup, lookup)
  }
  else if (needAddContext && "cc_context" in lookup["additionalInfo"]) {
    addContexts(sessionDiv, popup, lookup)
  }
  else {
    if (isCodeGeneration) {
      addDiffView(sessionDiv, popup, lookup, codeElement.innerText);
    } else {
      addSuggestions(sessionDiv, popup, lookup);
    }
  }
  sessionDiv.appendChild(popup)
}

// Add the `addDiffView` function
function addDiffView(sessionDiv, popup, lookup, originalText) {
  const lineDiff = new Diff();

  sessionDiv.classList.add("diffView")
  sessionDiv.classList.remove("features", "contexts","suggestions")
  const diffDiv = document.createElement("DIV");
  diffDiv.setAttribute("class", "diffView");

  const suggestionsText = lookup["suggestions"].map(s => s.presentationText).join("\n");

  const unifiedDiff = lineDiff.unifiedSlideDiff(originalText, suggestionsText, 1);

  unifiedDiff.forEach(line => {
    const lineDiv = document.createElement("DIV");
    lineDiv.textContent = line.content;
    lineDiv.style.whiteSpace = "pre"; // Ensure indentation is preserved

    const oldLineNumberSpan = document.createElement("span");
    oldLineNumberSpan.textContent = line.oldLineNumber !== '' ? line.oldLineNumber : ' ';
    oldLineNumberSpan.style.width = '30px';
    oldLineNumberSpan.style.display = 'inline-block';

    const newLineNumberSpan = document.createElement("span");
    newLineNumberSpan.textContent = line.newLineNumber !== '' ? line.newLineNumber : ' ';
    newLineNumberSpan.style.width = '30px';
    newLineNumberSpan.style.display = 'inline-block';

    if (line.type === "added") {
      lineDiv.style.color = "green";
    } else if (line.type === "removed") {
      lineDiv.style.color = "red";
    } else {
      lineDiv.style.color = "black";
    }

    lineDiv.prepend(newLineNumberSpan);
    lineDiv.prepend(oldLineNumberSpan);
    diffDiv.appendChild(lineDiv);
  });

  popup.appendChild(diffDiv);
}

function addCommonFeatures(sessionDiv, popup, lookup) {
  sessionDiv.classList.add("features")
  sessionDiv.classList.remove("contexts", "diffView","suggestions")
  const parts = sessionDiv.id.split(" ")
  const sessionId = parts[0]
  const lookupOrder = parts[1]
  if (sessionId in features) {
    const featuresJson = JSON.parse(pako.ungzip(atob(features[sessionId]), {to: 'string'}))
    const commonFeatures = featuresJson[lookupOrder]["common"]
    for (let groupName in favoriteFeatures) {
      if (!(groupName in commonFeatures)) continue
      for (let name of favoriteFeatures[groupName].filter(it => it in commonFeatures[groupName])) {
        popup.appendChild(createFeatureDiv(groupName, name, commonFeatures[groupName][name], true))
      }
    }
    for (let groupName in commonFeatures) {
      for (let name in commonFeatures[groupName]) {
        if (groupName in favoriteFeatures && favoriteFeatures[groupName].includes(name)) continue
        popup.appendChild(createFeatureDiv(groupName, name, commonFeatures[groupName][name], false))
      }
    }
  }
  addRelevanceModelBlock(popup, lookup, "trigger")
  addRelevanceModelBlock(popup, lookup, "filter")
  addAiaDiagnosticsBlock("Response", "aia_response", popup, lookup)
  addAiaDiagnosticsBlock("Context", "aia_context", popup, lookup)
  addDiagnosticsBlock("RAW SUGGESTIONS", "raw_proposals", popup, lookup)
  addDiagnosticsBlock("RAW FILTERED", "raw_filtered", popup, lookup)
  addDiagnosticsBlock("ANALYZED SUGGESTIONS", "analyzed_proposals", popup, lookup)
  addDiagnosticsBlock("ANALYZED FILTERED", "analyzed_filtered", popup, lookup)
  addDiagnosticsBlock("RESULT SUGGESTIONS", "result_proposals", popup, lookup)
}

function addContexts(sessionDiv, popup, lookup) {
  sessionDiv.classList.add("contexts")
  sessionDiv.classList.remove("features", "diffView","suggestions")

  if (!("cc_context" in lookup["additionalInfo"])) return

  const contextJson = lookup["additionalInfo"]["cc_context"]
  addButtonToCopyCompletionContext(contextJson, sessionDiv, popup, lookup)

  if (contextJson !== "") {
    const contextObject = JSON.parse(contextJson)
    contextObject.context.forEach(item => {
      popup.appendChild(createContextBlock(item))
    })
  }
}

function createContextBlock(context) {
  const contextBlock = document.createElement("DIV");
  contextBlock.style.whiteSpace = "inherit"
  const codeElement = createCodeElement(context)
  contextBlock.appendChild(codeElement)
  return contextBlock
}

function createCodeElement(context) {
  const code = document.createElement("code")
  code.innerHTML = `<b>File: ${context.filepath}</b><br><b>Type: ${context.type}</b><br><pre>${context.content}</pre>`
  code.style.whiteSpace = "inherit"
  return code
}

function addButtonToCopyCompletionContext(context, sessionDiv, popup, lookup) {
  let buttonDiv = document.createElement("DIV")
  let button = document.createElement("BUTTON")
  button.textContent = "Copy Context"
  buttonDiv.appendChild(button)
  popup.appendChild(buttonDiv)

  button.addEventListener("click", async function () {
    await navigator.clipboard.writeText(context)
  })
}

function addSuggestions(sessionDiv, popup, lookup) {
  sessionDiv.classList.add("suggestions")
  sessionDiv.classList.remove("features", "contexts")
  const sessionId = sessionDiv.id.split(" ")[0]
  const suggestions = lookup["suggestions"]
  for (let i = 0; i < suggestions.length; i++) {
    let suggestionDiv = document.createElement("DIV")
    suggestionDiv.setAttribute("class", "suggestion")
    suggestionDiv.setAttribute("id", `${sessionDiv.id} ${i}`)
    let p = document.createElement("pre")
    p.setAttribute("class", "suggestion-p")
    if (lookup["selectedPosition"] === i) {
      p.setAttribute("style", "font-weight: bold;")
    }
    const presentationText = suggestions[i].presentationText.replace(/</g, '&lt;').replace(/>/g, '&gt;')
    p.innerHTML = removeCommonIndentFromCodeSnippet(presentationText)
    suggestionDiv.appendChild(p)
    popup.appendChild(suggestionDiv)
  }
}

function addRelevanceModelBlock(popup, lookup, relevanceMode) {
  if (!(`${relevanceMode}_score` in lookup["additionalInfo"] && `${relevanceMode}_decision` in lookup["additionalInfo"])) return
  let addInfo = lookup["additionalInfo"]
  let relevanceModelResults = document.createElement("DIV")
  relevanceModelResults.innerHTML = `${relevanceMode} model score:` + addInfo[`${relevanceMode}_score`]
    + ", decision: " + addInfo[`${relevanceMode}_decision`]
  popup.appendChild(relevanceModelResults)
}

function addAiaDiagnosticsBlock(description, field, popup, lookup) {
  if (!(field in lookup["additionalInfo"])) return
  let contextBlock = document.createElement("DIV")
  contextBlock.style.whiteSpace = "inherit"
  let code = document.createElement("code")
  code.textContent = `${description}:\n\n${lookup["additionalInfo"][field]}`
  contextBlock.appendChild(code)
  code.style.whiteSpace = "inherit"
  popup.appendChild(contextBlock)
}

// thanks to AI Assistant
function addDiagnosticsBlock(description, field, popup, lookup) {
  if (!(field in lookup["additionalInfo"])) return

  const diagnostics = lookup["additionalInfo"][field];

  // Create a table if it doesn't exist yet
  let table = popup.querySelector("table")
  if (!table) {
    table = document.createElement("table")
    table.style.borderCollapse = "collapse"
    popup.appendChild(table)
  }

  // Create a new row
  let row = table.insertRow()

  // Cell for the header
  let headerCell = document.createElement("th")

  let headerSpan = document.createElement("span")
  headerSpan.innerHTML = description
  headerSpan.style.fontWeight = "normal"
  headerSpan.style.fontSize = "small"

  headerCell.appendChild(headerSpan)
  row.appendChild(headerCell)

  // Cell for the text
  let textCell = row.insertCell()
  textCell.style.paddingLeft = "10px"
  textCell.style.verticalAlign = "top"

  let ul = document.createElement("ul")
  ul.style.listStyleType = "disc"
  ul.style.marginTop = "0"
  ul.style.marginBottom = "0"

  let elements = 0
  for (let i = 0; i < diagnostics.length; i++) {
    if (diagnostics[i]["second"] == null) continue

    elements++

    let li = document.createElement("li")
    let code = addDiagnosticsItem(diagnostics, i)

    li.appendChild(code)
    ul.appendChild(li)
  }
  if (elements === 0) {
    row.style.color = "#CCC"
  }
  row.style.backgroundColor = "#FFF"

  textCell.appendChild(ul)

  // Add thin gray lines between rows
  let rows = table.rows
  for (let i = 1; i < rows.length; i++) {
    let row = rows[i]
    row.style.borderTop = "1px solid #CCC"

    // Add spacing between rows
    let cells = row.cells
    for (let j = 0; j < cells.length; j++) {
      cells[j].style.paddingTop = "5px"
    }
  }
}

function addDiagnosticsItem(diagnostics, num) {
  let codeContainer = document.createElement("DIV")
  let code = document.createElement("pre")
  code.innerHTML = removeCommonIndentFromCodeSnippet(diagnostics[num]["first"])
  let meta = document.createElement("pre")
  meta.innerHTML = "(" + diagnostics[num]["second"] + ")"
  codeContainer.appendChild(meta)
  codeContainer.appendChild(code)
  return codeContainer
}

/**
 * Removes the common leading indent from a multi-line code snippet, while keeping the first line unchanged.
 *
 * @param {string} code - The multi-line code snippet from which the common leading indentation will be removed.
 * @return {string} - The code snippet with the common leading indentation removed from all lines except the first one.
 */
function removeCommonIndentFromCodeSnippet(code) {
  const lines = code.split('\n')
  const offsets = lines.slice(1)
    .filter(line => line.length !== 0)
    .map(line => line.search(/\S/))
  const minOffset = Math.min(...offsets)
  if (minOffset <= 0) return code
  const trimmedLines = lines.slice(1).map(line => line.slice(minOffset)).join('\n')
  return lines[0] + '\n' + trimmedLines
}

function updateElementFeatures(suggestionDiv) {
  if (suggestionDiv.childElementCount === 2) {
    suggestionDiv.removeChild(suggestionDiv.childNodes[1])
    return
  }
  const parts = suggestionDiv.id.split(" ")
  const sessionId = parts[0]
  const lookupOrder = parts[1]
  const suggestionIndex = parts[2]

  if (suggestionDiv.parentElement?.parentElement?.classList.contains("chat")) {
    addFunctionCallingDiagnostics(suggestionDiv, sessions[sessionId]?._lookups[lookupOrder]?.suggestions[suggestionIndex])
    return;
  }

  if (!(sessionId in features)) return
  const featuresJson = JSON.parse(pako.ungzip(atob(features[sessionId]), {to: 'string'}))
  const lookupFeatures = featuresJson[lookupOrder]
  if (lookupFeatures["element"].length <= suggestionIndex) return
  const elementFeatures = lookupFeatures["element"][suggestionIndex]
  const featuresDiv = document.createElement("DIV")
  suggestionDiv.appendChild(featuresDiv)
  if ("element" in favoriteFeatures) {
    for (let name of favoriteFeatures["element"].filter(it => it in elementFeatures)) {
      featuresDiv.appendChild(createFeatureDiv("element", name, elementFeatures[name], true))
    }
  }
  for (let name in elementFeatures) {
    if ("element" in favoriteFeatures && favoriteFeatures["element"].includes(name)) continue
    featuresDiv.appendChild(createFeatureDiv("element", name, elementFeatures[name], false))
  }
}

function createFeatureDiv(type, name, value, isInFavorite) {
  const feature = document.createElement("DIV")
  if (type === "element") feature.classList.add("element-feature")
  feature.classList.add("feature-value")
  const inFavoriteClass = isInFavorite ? "in-favorite" : ""
  const typeName = type === "element" ? "" : ` (${type})`
  feature.innerHTML = `${name}${typeName}: ${value}<button onclick="selectFavoriteFeature('${type}', '${name}')" class="favorite-button ${inFavoriteClass}">&#x2605;</button>`
  return feature
}

function getFavoriteFeaturesFromCookie() {
  const value = "; " + document.cookie
  const parts = value.split("; favoriteFeatures=")
  if (parts.length === 2) return JSON.parse(parts.pop().split(";").shift())
  return {}
}

function saveFavoriteFeaturesToCookie() {
  const expiryDate = new Date()
  expiryDate.setMonth(expiryDate.getMonth() + 1)
  document.cookie = `favoriteFeatures = ${JSON.stringify(favoriteFeatures)}; path=/; expires=${expiryDate.toGMTString()}`
}

const selectElement = document.querySelector(".delimiter-pick")
const allStyles = new RegExp(`(${Array.apply(null, selectElement.options).map(option => option.value).join("|")})`)

const defType = localStorage.getItem(LC_KEYS.delimiter)
if (defType) {
  selectElement.value = defType ?? "delimiter"
  changeDelType(defType)
}

selectElement.addEventListener('change', event => changeDelType(event.target.value))

function changeDelType(type) {
  localStorage.setItem(LC_KEYS.delimiter, type)
  const code = document.querySelector(".cg-file")
  code.className = code.className.replace(allStyles, type)
}

function showSession(event, evaluation) {
  const evalContent = document.getElementsByClassName("evalContent")
  for (let i = 0; i < evalContent.length; i++) {
    evalContent[i].style.display = "none"
  }
  const tabLinks = document.getElementsByClassName("tablinks")
  for (let i = 0; i < tabLinks.length; i++) {
    tabLinks[i].className = tabLinks[i].className.replace(" active", "")
  }
  document.getElementById(evaluation).style.display = "block"
  event.currentTarget.className += " active"
}

const hiddenRows = {}

function invertRows(event, key) {
  if (hiddenRows[key]) {
    delete hiddenRows[key]
    document.querySelectorAll("." + key).forEach(el => {
      if (el.tagName === "TR") {
        el.classList.remove("stats-hidden")
      } else {

      }
    })
    event.currentTarget.classList.remove("stats-hidden")
  }
  else {
    hiddenRows[key] = true
    document.querySelectorAll("." + key).forEach(el => {
      if (el.tagName === "TR") {
        el.classList.add("stats-hidden")
      } else {

      }
    })
    event.currentTarget.classList.add("stats-hidden")
  }
}

function updateMultilinePopup(event) {
  if (event.altKey) {
    showMultilinePrefixAndSuffix(event)
    return
  }
  const target = event.target
  if (target.closest(".line-numbers") != null) {
    showMetrics()
    return
  }
  const suggestionDiv = target.closest(".suggestion")
  const attachmentsDiv = target.closest(".attachments")
  const showSuggestion =  attachmentsDiv != null || target.classList.contains("session")
  if (suggestionDiv == null && !showSuggestion) {
    if (target.closest(".autocomplete-items") == null) {
      closeAllLists();
    }
    return
  }
  const sessionDiv = target.closest(".session")
  closeAllLists()
  const lookup = getLookup(sessionDiv)
  const popup = document.createElement("DIV")
  popup.setAttribute("class", "autocomplete-items")

  addMultilineHeaders(popup, showSuggestion)
  let context = getMultilineContext(sessionDiv)
  let indent = "prefix" in context ? context.prefix.match(/ *$/)[0].length : 0
  let expectedText = sessions[sessionDiv.id.split(" ")[0]]["expectedText"].replace(new RegExp(`^ {${indent}}`, 'gm'), '')
  if (showSuggestion) {
    addMultilineSuggestion(sessionDiv, popup, lookup)
    addMultilineExpectedText(popup, expectedText)
    addCommonFeatures(sessionDiv, popup, lookup)
  }
  else {
    addMultilineAttachments(sessionDiv, popup, expectedText)
  }
  sessionDiv.appendChild(popup)
}

function addMultilineHeaders(popup, showSuggestion) {
  const header = document.createElement("DIV")
  if (showSuggestion) {
    header.setAttribute("class", "suggestion-header")
  }
  else {
    header.setAttribute("class", "attachments-header")
    header.innerHTML = "attachments"
  }
  const expectedHeader = document.createElement("DIV")
  expectedHeader.setAttribute("class", "expected-header")
  expectedHeader.innerHTML = "expected"
  popup.appendChild(header)
  popup.appendChild(expectedHeader)
}

function addMultilineSuggestion(sessionDiv, popup, lookup) {
  addSuggestions(sessionDiv, popup, lookup)
  if (popup.children.length < 3) {
    let suggestionDiv = document.createElement("DIV")
    suggestionDiv.setAttribute("class", "suggestion")
    let p = document.createElement("pre")
    p.innerHTML = "\t\t\t\tNO SUGGESTION\t\t\t\t"
    suggestionDiv.appendChild(p)
    popup.appendChild(suggestionDiv)
  }
}

function addMultilineAttachments(sessionDiv, popup, expectedText) {
  const context = getMultilineContext(sessionDiv)
  let attachmentsDiv = document.createElement("DIV")
  attachmentsDiv.setAttribute("class", "attachments")
  let p = document.createElement("pre")
  p.innerHTML = context.attachments
  attachmentsDiv.appendChild(p)
  popup.appendChild(attachmentsDiv)

  const expected = document.createElement("DIV")
  expected.setAttribute("class", "expected")
  const pExp = document.createElement("pre")
  pExp.innerHTML = expectedText

  let contextTokens = (context.attachments + "\n" + context.prefix + "\n" + context.suffix).split((/\W+/))
  let expectedTokens = expectedText.split((/\W+/))
  expectedTokens.forEach((token) => {
    if (contextTokens.indexOf(token) === -1) {
      pExp.innerHTML = pExp.innerHTML.replace(new RegExp('\\b' + token + '\\b'), '<span class="missing-context">$&</span>')
    }
  })
  expected.appendChild(pExp)
  popup.appendChild(expected)
}

function getMultilineContext(sessionDiv) {
  const parts = sessionDiv.id.split(" ")
  const sessionId = parts[0]
  const lookupOrder = parts[1]
  const featuresJson = JSON.parse(pako.ungzip(atob(features[sessionId]), {to: 'string'}))
  return featuresJson[lookupOrder]["common"].context
}

function addMultilineExpectedText(popup, expectedText) {
  const expected = document.createElement("DIV")
  expected.setAttribute("class", "expected")
  const p = document.createElement("pre")
  p.innerHTML = removeCommonIndentFromCodeSnippet(expectedText)
  expected.appendChild(p)
  popup.appendChild(expected)
}

function showMultilinePrefixAndSuffix(event) {
  if (event.target.classList.contains("session")) {
    const sessionDiv = event.target
    sessionDiv.parentNode.style.display = "none"
    const newCode = document.createElement("pre")
    newCode.setAttribute("class", "code context multiline")
    newCode.style.backgroundColor = "bisque"
    let context = getMultilineContext(sessionDiv)
    let prefix = context.prefix
    let suffix = context.suffix

    let prev = sessionDiv.previousSibling
    let offset = ""
    while (prev != null) {
      offset = prev.textContent + offset
      prev = prev.previousSibling
    }
    let begin = "\n".repeat(offset.split('\n').length - prefix.split('\n').length)
    let expectedText = sessions[sessionDiv.id.split(" ")[0]]["expectedText"]
    newCode.innerHTML = begin + prefix + "<span style='background-color: white'>" + expectedText +"</span>" + suffix
    sessionDiv.parentNode.parentNode.prepend(newCode)
  }
  else if (event.target.closest(".code.context") != null) {
    const newCode = event.target.closest(".code.context")
    newCode.parentNode.lastElementChild.style.removeProperty("display")
    newCode.remove()
  }
}

function showMetrics() {
  let metricsDiv = document.getElementById("metrics-column")
  metricsDiv.style.display = metricsDiv.style.display === "none" ? "" : "none"
}

document.getElementById("defaultTabOpen")?.click()
