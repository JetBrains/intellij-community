

document.addEventListener("click", function (e) {
  if (e.target.closest(".popup-content") != null) {
    return;
  }

  if (e.target.tagName === "A") { // TODO condition, remove onClick?
    return;
  }

  closePopup();
});

function openDiff(element, original, suggested) {
  closePopup();

  const diff = renderDiff(original, suggested);
  diff.setAttribute("class", "popup-content");
  element.appendChild(diff);
  element.style.visibility = "visible";
}

function openText(element, text, wrapping = false) {
  closePopup();

  const highlighted = highlightedText(text)
  highlighted.setAttribute("class", "popup-content");
  if (wrapping) {
    highlighted.setAttribute("style", "white-space: pre-wrap;");
  }
  element.appendChild(highlighted);
  element.style.visibility = "visible";
}

function closePopup() {
  for (const element of document.getElementsByClassName("popup-container")) {
    while (element.firstChild) {
      element.removeChild(element.firstChild);
    }
    element.style.visibility = "hidden";
  }
}