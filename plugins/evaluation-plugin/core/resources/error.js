copyBtn = document.getElementById("copyBtn");
copyBtn.style.fontSize = '20px';
copyBtn.style.padding = '0 0 0 4px';
copyBtn.title = 'Copy to Clipboard';
copyBtn.onclick = function () {
    let textArea = document.createElement("textarea");
    textArea.style.position = 'fixed';
    textArea.style.top = '0';
    textArea.style.left = '0';
    textArea.style.width = '2em';
    textArea.style.height = '2em';
    textArea.style.padding = '0';
    textArea.style.border = 'none';
    textArea.style.outline = 'none';
    textArea.style.boxShadow = 'none';
    textArea.style.background = 'transparent';
    textArea.value = document.getElementById('stackTrace').textContent;
    document.body.appendChild(textArea);
    textArea.focus();
    textArea.select();
    try {
        document.execCommand('copy')
    } catch (e) {
        console.log("Did not copy :(")
    }
    document.body.removeChild(textArea)
};