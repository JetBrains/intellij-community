const favoriteFeatures = getFavoriteFeaturesFromCookie()
isCompletionGolf = false

const prefix = "ep@"
const LC_KEYS = {
  delimiter: prefix + "delimiter"
}

document.addEventListener("click", function (e) {
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
  else if (e.target.classList.contains("completion")) {
    updatePopup(e.target)
  }
  else if (suggestionDiv != null) {
    updateElementFeatures(suggestionDiv)
  }
  else {
    closeAllLists()
  }
})

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
    const sessionId = getId(sessionDiv).split(" ")[0]
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

function getId(sessionDiv) {
  if (isCompletionGolf) {
    return sessionDiv.dataset.id
  }
  else {
    return sessionDiv.id
  }
}

function updatePopup(sessionDiv) {
  const lookup = getLookup(sessionDiv)
  const popup = document.createElement("DIV")
  popup.setAttribute("class", "autocomplete-items")
  const prefixDiv = document.createElement("DIV")
  prefixDiv.setAttribute("style", "background-color: lightgrey;")
  prefixDiv.innerHTML = `prefix: &quot;${lookup["prefix"]}&quot;; latency: ${lookup["latency"]}`
  popup.appendChild(prefixDiv)
  const needAddFeatures = sessionDiv.classList.contains("suggestions")
  closeAllLists()
  if (!isCompletionGolf && needAddFeatures) {
    addCommonFeatures(sessionDiv, popup)
  }
  else {
    addSuggestions(sessionDiv, popup, lookup)
  }
  sessionDiv.appendChild(popup)
}

function addCommonFeatures(sessionDiv, popup) {
  sessionDiv.classList.add("features")
  const parts = sessionDiv.id.split(" ")
  const sessionId = parts[0]
  const lookupOrder = parts[1]
  if (!(sessionId in features)) return
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

function addSuggestions(sessionDiv, popup, lookup) {
  sessionDiv.classList.add("suggestions")
  sessionDiv.classList.remove("features")
  const sessionId = getId(sessionDiv).split(" ")[0]
  const suggestions = lookup["suggestions"]
  for (let i = 0; i < suggestions.length; i++) {
    let suggestionDiv = document.createElement("DIV")
    suggestionDiv.setAttribute("class", "suggestion")
    suggestionDiv.setAttribute("id", `${getId(sessionDiv)} ${i}`)
    let p = document.createElement(isCompletionGolf ? "code" : "plaintext")
    p.setAttribute("class", "suggestion-p")
    if (sessions[sessionId].expectedText == suggestions[i].text) {
      p.setAttribute("style", "font-weight: bold;")
    }
    p.innerHTML = suggestions[i].presentationText
    suggestionDiv.appendChild(p)
    popup.appendChild(suggestionDiv)
  }
}

function updateElementFeatures(suggestionDiv) {
  if (suggestionDiv.childElementCount === 2) {
    suggestionDiv.removeChild(suggestionDiv.childNodes[1])
    return
  }
  const parts = suggestionDiv.id.split(" ")
  const sessionId = parts[0]
  if (!(sessionId in features)) return
  const lookupOrder = parts[1]
  const suggestionIndex = parts[2]
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

document.getElementById("defaultTabOpen")?.click()
