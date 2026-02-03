

document.addEventListener("click", function (e) {
  if (e.target.closest(".popup-container") != null) {
    return
  }

  if (e.target.tagName === "A") { // TODO condition, remove onClick?
    return;
  }

  closePopup();
});

function openDiff(popupId, original, suggested, description = null) {
  const diff = renderDiff(original, suggested);
  showPopup(popupId, diff, description);
}

function openText(popupId, text, description = null, wrapping = false) {
  const highlighted = highlightedText(text)
  if (wrapping) {
    highlighted.setAttribute("style", "white-space: pre-wrap;");
  }
  showPopup(popupId, highlighted, description);
}

function openSnippets(popupId, snippets) {
  const renderedSnippets = renderSnippets(snippets)
  showPopup(popupId, renderedSnippets)
}

function renderSnippets(snippets) {
  const container = document.createElement('div')
  snippets.forEach(snippet => {
    const pre = document.createElement('pre')
    pre.textContent = snippet
    pre.style.marginBottom = '10px'
    pre.style.padding = '8px'
    pre.style.borderBottom = '1px solid #DEDEDE'
    container.appendChild(pre)
  })
  return container
}

function showPopup(popupId, element, description = null) {
  closePopup();
  element.setAttribute("class", "popup-content");
  if (description != null) {
    document.getElementById(`${popupId}-description`).textContent = description;
  }
  document.getElementById(`${popupId}-content`).appendChild(element);
  document.getElementById(popupId).style.visibility = "visible";
}

function closePopup() {
  for (const element of document.getElementsByClassName("popup-content")) {
    element.remove();
  }

  for (const element of document.getElementsByClassName("popup-container")) {
    element.style.visibility = "hidden";
  }

  for (const element of document.getElementsByClassName("popup-container-description")) {
    element.textContent = "";
  }
}

function copyPopupText(containerId) {
  const container = document.getElementsByClassName("popup-content")[0];
  if (!container) {
    return;
  }

  const tempDiv = document.createElement('div');
  tempDiv.innerHTML = container.innerHTML;

  const skipElements = tempDiv.querySelectorAll('.copy-text-ignore');
  skipElements.forEach(element => {
    element.remove();
  });

  tempDiv.innerHTML = tempDiv.innerHTML.replace(/<br\s*\/?>/gi, '\n');

  tempDiv.innerHTML = tempDiv.innerHTML.replace(/<\/div>/gi, '</div>\n');

  const text = tempDiv.textContent || '';

  // Create a temporary textarea to copy from
  const textarea = document.createElement('textarea');
  textarea.value = text;
  textarea.style.position = 'fixed';  // Make it invisible
  document.body.appendChild(textarea);
  textarea.select();

  try {
    const successful = document.execCommand('copy');

    // Visual feedback
    const copyButton = document.getElementById(`${containerId}-copy-button`);
    if (copyButton) {
      const originalText = copyButton.textContent;
      copyButton.textContent = successful ? 'Copied!' : 'Failed!';

      setTimeout(() => {
        copyButton.textContent = originalText;
      }, 2000);
    }
  } catch (err) {
    console.error('Unable to copy text: ', err);
  } finally {
    document.body.removeChild(textarea);
  }
}