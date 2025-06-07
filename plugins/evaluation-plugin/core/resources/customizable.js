

document.addEventListener("click", function (e) {
  if (e.target.closest(".popup-container") != null) {
    return
  }

  if (e.target.tagName === "A") { // TODO condition, remove onClick?
    return;
  }

  closePopup();
});

function openDiff(popupId, original, suggested) {
  const diff = renderDiff(original, suggested);
  showPopup(popupId, diff);
}

function openText(popupId, text, wrapping = false) {
  const highlighted = highlightedText(text)
  if (wrapping) {
    highlighted.setAttribute("style", "white-space: pre-wrap;");
  }
  showPopup(popupId, highlighted);
}

function showPopup(popupId, element) {
  closePopup();
  element.setAttribute("class", "popup-content");
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