// followlines.js - JavaScript utilities for followlines UI
//
// Copyright 2017 Logilab SA <contact@logilab.fr>
//
// This software may be used and distributed according to the terms of the
// GNU General Public License version 2 or any later version.

//** Install event listeners for line block selection and followlines action */
document.addEventListener('DOMContentLoaded', function() {
    var sourcelines = document.getElementsByClassName('sourcelines')[0];
    if (typeof sourcelines === 'undefined') {
        return;
    }
    // URL to complement with "linerange" query parameter
    var targetUri = sourcelines.dataset.logurl;
    if (typeof targetUri === 'undefined') {
        return;
    }

    // Tag of children of "sourcelines" element on which to add "line
    // selection" style.
    var selectableTag = sourcelines.dataset.selectabletag;
    if (typeof selectableTag === 'undefined') {
        return;
    }

    var isHead = parseInt(sourcelines.dataset.ishead || "0");

    //* position "element" on top-right of cursor */
    function positionTopRight(element, event) {
        var x = (event.clientX + 10) + 'px',
            y = (event.clientY - 20) + 'px';
        element.style.top = y;
        element.style.left = x;
    }

    // retrieve all direct *selectable* children of class="sourcelines"
    // element
    var selectableElements = Array.prototype.filter.call(
        sourcelines.children,
        function(x) { return x.tagName === selectableTag; });

    var btnTitleStart = 'start following lines history from here';
    var btnTitleEnd = 'terminate line block selection here';

    //** return a <button> element with +/- spans */
    function createButton() {
        var btn = document.createElement('button');
        btn.title = btnTitleStart;
        btn.classList.add('btn-followlines');
        var plusSpan = document.createElement('span');
        plusSpan.classList.add('followlines-plus');
        plusSpan.textContent = '+';
        btn.appendChild(plusSpan);
        var br = document.createElement('br');
        btn.appendChild(br);
        var minusSpan = document.createElement('span');
        minusSpan.classList.add('followlines-minus');
        minusSpan.textContent = '−';
        btn.appendChild(minusSpan);
        return btn;
    }

    // extend DOM with CSS class for selection highlight and action buttons
    var followlinesButtons = [];
    for (var i = 0; i < selectableElements.length; i++) {
        selectableElements[i].classList.add('followlines-select');
        var btn = createButton();
        followlinesButtons.push(btn);
        // insert the <button> as child of `selectableElements[i]` unless the
        // latter has itself a child  with a "followlines-btn-parent" class
        // (annotate view)
        var btnSupportElm = selectableElements[i];
        var childSupportElms = btnSupportElm.getElementsByClassName(
            'followlines-btn-parent');
        if ( childSupportElms.length > 0 ) {
            btnSupportElm = childSupportElms[0];
        }
        var refNode = btnSupportElm.childNodes[0]; // node to insert <button> before
        btnSupportElm.insertBefore(btn, refNode);
    }

    // ** re-initialize followlines buttons */
    function resetButtons() {
        for (var i = 0; i < followlinesButtons.length; i++) {
            var btn = followlinesButtons[i];
            btn.title = btnTitleStart;
            btn.classList.remove('btn-followlines-end');
            btn.classList.remove('btn-followlines-hidden');
        }
    }

    var lineSelectedCSSClass = 'followlines-selected';

    //** add CSS class on selectable elements in `from`-`to` line range */
    function addSelectedCSSClass(from, to) {
        for (var i = from; i <= to; i++) {
            selectableElements[i].classList.add(lineSelectedCSSClass);
        }
    }

    //** remove CSS class from previously selected lines */
    function removeSelectedCSSClass() {
        var elements = sourcelines.getElementsByClassName(
            lineSelectedCSSClass);
        while (elements.length) {
            elements[0].classList.remove(lineSelectedCSSClass);
        }
    }

    // ** return the element of type "selectableTag" parent of `element` */
    function selectableParent(element) {
        var parent = element.parentElement;
        if (parent === null) {
            return null;
        }
        if (element.tagName === selectableTag && parent.isSameNode(sourcelines)) {
            return element;
        }
        return selectableParent(parent);
    }

    // ** update buttons title and style upon first click */
    function updateButtons(selectable) {
        for (var i = 0; i < followlinesButtons.length; i++) {
            var btn = followlinesButtons[i];
            btn.title = btnTitleEnd;
            btn.classList.add('btn-followlines-end');
        }
        // on clicked button, change title to "cancel"
        var clicked = selectable.getElementsByClassName('btn-followlines')[0];
        clicked.title = 'cancel';
        clicked.classList.remove('btn-followlines-end');
    }

    //** add `listener` on "click" event for all `followlinesButtons` */
    function buttonsAddEventListener(listener) {
        for (var i = 0; i < followlinesButtons.length; i++) {
            followlinesButtons[i].addEventListener('click', listener);
        }
    }

    //** remove `listener` on "click" event for all `followlinesButtons` */
    function buttonsRemoveEventListener(listener) {
        for (var i = 0; i < followlinesButtons.length; i++) {
            followlinesButtons[i].removeEventListener('click', listener);
        }
    }

    //** event handler for "click" on the first line of a block */
    function lineSelectStart(e) {
        var startElement = selectableParent(e.target.parentElement);
        if (startElement === null) {
            // not a "selectable" element (maybe <a>): abort, keeping event
            // listener registered for other click with a "selectable" target
            return;
        }

        // update button tooltip text and CSS
        updateButtons(startElement);

        var startId = parseInt(startElement.id.slice(1));
        startElement.classList.add(lineSelectedCSSClass); // CSS

        // remove this event listener
        buttonsRemoveEventListener(lineSelectStart);

        //** event handler for "click" on the last line of the block */
        function lineSelectEnd(e) {
            var endElement = selectableParent(e.target.parentElement);
            if (endElement === null) {
                // not a <span> (maybe <a>): abort, keeping event listener
                // registered for other click with <span> target
                return;
            }

            // remove this event listener
            buttonsRemoveEventListener(lineSelectEnd);

            // reset button tooltip text
            resetButtons();

            // compute line range (startId, endId)
            var endId = parseInt(endElement.id.slice(1));
            if (endId === startId) {
                // clicked twice the same line, cancel and reset initial state
                // (CSS, event listener for selection start)
                removeSelectedCSSClass();
                buttonsAddEventListener(lineSelectStart);
                return;
            }
            var inviteElement = endElement;
            if (endId < startId) {
                var tmp = endId;
                endId = startId;
                startId = tmp;
                inviteElement = startElement;
            }

            addSelectedCSSClass(startId - 1, endId -1);  // CSS

            // append the <div id="followlines"> element to last line of the
            // selection block
            var divAndButton = followlinesBox(targetUri, startId, endId, isHead);
            var div = divAndButton[0],
                button = divAndButton[1];
            inviteElement.appendChild(div);
            // set position close to cursor (top-right)
            positionTopRight(div, e);
            // hide all buttons
            for (var i = 0; i < followlinesButtons.length; i++) {
                followlinesButtons[i].classList.add('btn-followlines-hidden');
            }

            //** event handler for cancelling selection */
            function cancel() {
                // remove invite box
                div.parentNode.removeChild(div);
                // restore initial event listeners
                buttonsAddEventListener(lineSelectStart);
                buttonsRemoveEventListener(cancel);
                for (var i = 0; i < followlinesButtons.length; i++) {
                    followlinesButtons[i].classList.remove('btn-followlines-hidden');
                }
                // remove styles on selected lines
                removeSelectedCSSClass();
                resetButtons();
            }

            // bind cancel event to click on <button>
            button.addEventListener('click', cancel);
            // as well as on an click on any source line
            buttonsAddEventListener(cancel);
        }

        buttonsAddEventListener(lineSelectEnd);

    }

    buttonsAddEventListener(lineSelectStart);

    //** return a <div id="followlines"> and inner cancel <button> elements */
    function followlinesBox(targetUri, fromline, toline, isHead) {
        // <div id="followlines">
        var div = document.createElement('div');
        div.id = 'followlines';

        //   <div class="followlines-cancel">
        var buttonDiv = document.createElement('div');
        buttonDiv.classList.add('followlines-cancel');

        //     <button>x</button>
        var button = document.createElement('button');
        button.textContent = 'x';
        buttonDiv.appendChild(button);
        div.appendChild(buttonDiv);

        //   <div class="followlines-link">
        var aDiv = document.createElement('div');
        aDiv.classList.add('followlines-link');
        aDiv.textContent = 'follow history of lines ' + fromline + ':' + toline + ':';
        var linesep = document.createElement('br');
        aDiv.appendChild(linesep);
        //     link to "ascending" followlines
        var aAsc = document.createElement('a');
        var url = targetUri + '?patch=&linerange=' + fromline + ':' + toline;
        aAsc.setAttribute('href', url);
        aAsc.textContent = 'older';
        aDiv.appendChild(aAsc);

        if (!isHead) {
            var sep = document.createTextNode(' / ');
            aDiv.appendChild(sep);
            //     link to "descending" followlines
            var aDesc = document.createElement('a');
            aDesc.setAttribute('href', url + '&descend=');
            aDesc.textContent = 'newer';
            aDiv.appendChild(aDesc);
        }

        div.appendChild(aDiv);

        return [div, button];
    }

}, false);
