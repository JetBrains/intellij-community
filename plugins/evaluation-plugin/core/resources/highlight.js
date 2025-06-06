
const ERROR_HIGHLIGHT = " <<<----<<< ";
const WARNING_HIGHLIGHT = " <<<~~~~<<< ";
const SUCCESS_HIGHLIGHT = " <<<++++<<< ";
const HIGHLIGHTS = [ERROR_HIGHLIGHT, WARNING_HIGHLIGHT, SUCCESS_HIGHLIGHT];

function highlightedText(text) {
  const container = [];
  let previousText = undefined;

  function addText(text, inlineComment) {
    let textElement;
    let resultElement;

    if (inlineComment !== undefined) {
      textElement = document.createElement("pre");
      textElement.innerText = text;
      textElement.style.display = "inline-block";

      const commentComponent = document.createElement("pre");
      commentComponent.classList.add("copy-text-ignore");
      commentComponent.style.marginLeft = "10px";
      commentComponent.innerText = inlineComment;
      commentComponent.style.textDecoration = "";
      commentComponent.style.color = "grey";

      resultElement = document.createElement("div");
      resultElement.style.display = "block ruby"
      resultElement.appendChild(textElement);
      resultElement.appendChild(commentComponent);
    }
    else {
      textElement = document.createElement("pre");
      textElement.innerText = text;
      resultElement = textElement;
    }

    const leadingNewLineCount = text.match(/^\n+/)?.[0]?.length || 0
    for (let i = 0; i < leadingNewLineCount; i++) {
      container.push(document.createElement("br"));
    }

    container.push(resultElement);

    const trailingNewLineCount = text.match(/\n+$/)?.[0]?.length || 0;
    for (let i = 0; i < trailingNewLineCount; i++) {
      container.push(document.createElement("br"));
    }

    return textElement;
  }

  for (const line of text.split('\n')) {
    const highlight = HIGHLIGHTS.find(highlight => line.includes(highlight));
    if (highlight !== undefined) {
      if (previousText !== undefined) {
        addText(previousText);
        previousText = undefined;
      }

      const [text, message] = line.split(highlight);

      const lineElement = addText(text, message);
      lineElement.style.textDecoration = "underline";
      lineElement.style.textDecorationThickness = "2px";
      if (highlight === ERROR_HIGHLIGHT) {
        lineElement.style.textDecorationColor = "red";
      }
      else if (highlight === WARNING_HIGHLIGHT) {
        lineElement.style.textDecorationColor = "orange";
      }
      else if (highlight === SUCCESS_HIGHLIGHT) {
        lineElement.style.textDecorationColor = "green";
      }
    }
    else {
      previousText = previousText === undefined ? line : `${previousText}\n${line}`;
    }
  }

  if (previousText !== undefined) {
    addText(previousText)
  }

  if (container.length === 1) {
    return container[0];
  }
  else {
    const div = document.createElement("div");
    div.append(...container);
    return div;
  }
}