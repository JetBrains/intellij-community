function addFunctionCallingDiagnostics(suggestionDiv, suggestion) {
  if (suggestion?.details?.response) {
    const response = prettify(suggestion.details.response);
    suggestionDiv.appendChild(Object.assign(document.createElement("pre"), { innerHTML: response }));
  }
}

function prettify(str) {
  try {
    return JSON.stringify(JSON.parse(str),null,2);
  } catch (e) {
    return str;
  }
}